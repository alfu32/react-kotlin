package org.github.alfu32.ktx

import java.io.InputStream
import java.io.PrintStream
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.max

data class StyleSet(
    var top: Int? = null,
    var left: Int? = null,
    var bottom: Int? = null,
    var right: Int? = null,
    var bg: VtColor? = null,
    var fg: VtColor? = null,
    var textDecoration: String? = null,
    var borderSet: String? = null,
    var lineSet: String? = null
) {
    // Copy non-null fields from src into this where this is null
    fun mergeFrom(src: StyleSet) {
        if (top == null) top = src.top
        if (left == null) left = src.left
        if (bottom == null) bottom = src.bottom
        if (right == null) right = src.right
        if (bg == null) bg = src.bg
        if (fg == null) fg = src.fg
        if (textDecoration == null) textDecoration = src.textDecoration
        if (borderSet == null) borderSet = src.borderSet
        if (lineSet == null) lineSet = src.lineSet
    }

    fun merged(src: StyleSet): StyleSet =
        this.copy().also { it.mergeFrom(src) }

    companion object {
        // Parse a single style block:
        // "top: 1; left: 2; bg: #ff0000; text-decoration: bold;"
        fun parse(def: String): StyleSet {
            val style = StyleSet()
            val entries = def.split(';', '\n', '\r')
            for (raw in entries) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val parts = line.split(':', limit = 2)
                if (parts.size < 2) continue
                val key = parts[0].trim()
                val valueRaw = parts[1].trim()
                if (valueRaw.isEmpty() || valueRaw == "none") continue

                when (key) {
                    "top"    -> style.top = valueRaw.toIntOrNull()
                    "left"   -> style.left = valueRaw.toIntOrNull()
                    "bottom" -> style.bottom = valueRaw.toIntOrNull()
                    "right"  -> style.right = valueRaw.toIntOrNull()
                    "bg"     -> style.bg = parseColor(valueRaw)
                    "fg"     -> style.fg = parseColor(valueRaw)
                    "text-decoration" -> style.textDecoration = valueRaw
                    "border-set"      -> style.borderSet = valueRaw
                    "line-set"        -> style.lineSet = valueRaw
                    else -> { /* ignore unknown */ }
                }
            }
            return style
        }

        private fun parseColor(raw: String): VtColor {
            return VtColor.from(raw)
        }
    }
}

class StyleSheet(
    val defaultStyle: StyleSet = StyleSet(),
    private val rules: Map<String, StyleSet> = emptyMap()
) {
    // styleId matched against the last segment of a rule path.
    // Path is selector split on '.' (after normalisation).
    fun getStyle(styleId: String): StyleSet {
        // Find the most specific matching path (longest)
        val matchingKeys = rules.keys.filter { key ->
            val path = splitPathKey(key)
            path.isNotEmpty() && path.last() == styleId
        }
        if (matchingKeys.isEmpty()) {
            return defaultStyle.copy()
        }

        val bestKey = matchingKeys.maxByOrNull { splitPathKey(it).size }!!
        val bestPath = splitPathKey(bestKey)

        val result = defaultStyle.copy()
        val prefix = mutableListOf<String>()
        for (segment in bestPath) {
            prefix += segment
            val pk = prefix.joinToString(".")
            rules[pk]?.let { result.mergeFrom(it) }
        }
        return result
    }

    companion object {
        // Very small CSS-ish parser.
        // Example:
        //
        // default {
        //   bg: #000000;
        //   fg: #ffffff;
        // }
        //
        // root {
        //   top: 1;
        // }
        //
        // root.panel.button {
        //   text-decoration: bold;
        // }
        //
        fun parse(css: String): StyleSheet {
            val defaultStyle = StyleSet()
            val rules = mutableMapOf<String, StyleSet>()

            // Split on '}', then each chunk should contain "selector { body"
            for (chunk in css.split('}')) {
                val parts = chunk.split('{', limit = 2)
                if (parts.size < 2) continue

                val selectorRaw = parts[0].trim()
                if (selectorRaw.isEmpty()) continue

                val body = parts[1]
                val style = StyleSet.parse(body)

                if (selectorRaw == "default") {
                    defaultStyle.mergeFrom(style)
                } else {
                    // Normalise: treat spaces and '>' as separators, then join with '.'
                    val segments = selectorRaw
                        .replace(">", " ")
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }

                    if (segments.isNotEmpty()) {
                        val key = segments.joinToString(".")
                        rules[key] = style
                    }
                }
            }

            return StyleSheet(defaultStyle, rules)
        }

        private fun splitPathKey(key: String): List<String> =
            if (key.isBlank()) emptyList() else key.split('.')
    }
}

class StateStore {
    private val map = IdentityHashMap<Any, MutableMap<String, Any?>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> use(keyOwner: Any, key: String, initial: () -> T): Pair<T, (T) -> Unit> {
        val bucket = map.getOrPut(keyOwner) { mutableMapOf() }
        val current = bucket[key] as? T ?: initial().also { bucket[key] = it }
        return current to { v -> bucket[key] = v }
    }
}

fun main() {
    val ctx = AnsiVtDrawingContext(
        listener = VtEventListener { _, _ -> }, // placeholder; App will set itself
        targetFps = 60
    )

    val app = App(ctx)

    ctx.run()
}

/**
 * RGB color used by VtDrawingContext.
 * Components are in the inclusive range [0, 255].
 */
data class VtColor(
    val r: UByte,
    val g: UByte,
    val b: UByte
){
    companion object {
        fun from(rgb: Int):VtColor {
            val r=(rgb shr 16 and 0xFF).toUByte()
            val g=(rgb shr 8 and 0xFF).toUByte()
            val b=(rgb shr 0 and 0xFF).toUByte()
            return VtColor(r=r, g=g, b=b)
        }
        fun from(rgb: String):VtColor {
            val tt=rgb.replace("#","").toInt(16)
            return from(tt)
        }
    }
    fun toHex():String{
        return "#${r.toString(16)}${g.toString(16)}${b.toString(16)}"
    }
    fun toInt(): Int{
        return ((r.toLong() shl 16) + (g.toLong() shl 8) + (b.toLong() shl 0)).toInt()
    }
    fun adjust(v: UByte,p:Byte):UByte{
        if(p<0) {
           val clampedP =if(p*p > 10000) -100 else p
           val adjustment = clampedP.toInt() * v.toInt() / 100
           return (v.toInt() + adjustment).toUByte()
        } else {
            val clampedP =if(p*p > 10000) 100 else p
            val adjustment = clampedP.toInt() * (256-v.toInt()) / 100 - 1
            return (v.toInt() + adjustment).toUByte()
        }
    }
    operator fun times(proportion: Byte): VtColor {
        return VtColor(
            r = adjust(r, proportion),
            g = adjust(g, proportion),
            b = adjust(b, proportion),
        )
    }
    /**
     * Returns an ANSI 24-bit foreground color sequence, e.g. "\u001B[38;2;R;G;Bm".
     */
    fun foreground(): String {
        val rr = r.toInt() and 0xFF
        val gg = g.toInt() and 0xFF
        val bb = b.toInt() and 0xFF
        return "\u001B[38;2;$rr;$gg;${bb}m"
    }
    /**
     * Returns an ANSI 24-bit background color sequence, e.g. "\u001B[38;2;R;G;Bm".
     */
    fun background(): String {
        val rr = r.toInt() and 0xFF
        val gg = g.toInt() and 0xFF
        val bb = b.toInt() and 0xFF
        return "\u001B[48;2;$rr;$gg;${bb}m"
    }
}

/**
 * Kotlin interface corresponding to V's term.ui.Context drawing API.
 *
 * This abstracts a terminal drawing surface and its basic state.
 */
interface VtDrawingContext {

    var onFrame: ((VtDrawingContext) -> Unit)?

    /**
     * Number of frames rendered so far.
     * Mirrors Context.frame_count (u64).
     */
    val frameCount: Long

    /**
     * Current terminal window width in character cells.
     * Mirrors Context.window_width.
     */
    val windowWidth: Int

    /**
     * Current terminal window height in character cells.
     * Mirrors Context.window_height.
     */
    val windowHeight: Int

    /** Sets the character state to bold. */
    fun bold()

    /** Erases the entire terminal window and any saved lines. */
    fun clear()

    /**
     * Draws a dashed line segment from (x1, y1) to (x2, y2).
     */
    fun drawDashedLine(x1: Int, y1: Int, x2: Int, y2: Int)

    /**
     * Draws a rectangle with dashed lines, from top-left (x1, y1)
     * to bottom-right (x2, y2), with no fill.
     */
    fun drawEmptyDashedRect(x1: Int, y1: Int, x2: Int, y2: Int)

    /**
     * Draws a rectangle border (no fill), from top-left (x1, y1)
     * to bottom-right (x2, y2).
     */
    fun drawEmptyRect(x1: Int, y1: Int, x2: Int, y2: Int)

    /**
     * Draws a solid line segment from (x1, y1) to (x2, y2).
     */
    fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int)

    /**
     * Draws a single point at (x, y).
     */
    fun drawPoint(x: Int, y: Int)

    /**
     * Draws a filled rectangle from top-left (x1, y1)
     * to bottom-right (x2, y2).
     */
    fun drawRect(x1: Int, y1: Int, x2: Int, y2: Int)

    /**
     * Draws text starting at (x, y).
     */
    fun drawText(x: Int, y: Int, text: String)

    /**
     * Flushes the accumulated buffer to the terminal.
     */
    fun flush()

    /**
     * Makes the cursor invisible.
     */
    fun hideCursor()

    /**
     * Makes the cursor visible.
     */
    fun showCursor()

    /**
     * Draws a horizontal separator spanning the width of the screen at row y.
     */
    fun horizontalSeparator(y: Int)

    /**
     * Resets colors and text formatting to defaults.
     */
    fun reset()

    /**
     * Resets the background color to its default value.
     */
    fun resetBackgroundColor()

    /**
     * Resets the foreground color to its default value.
     */
    fun resetColor()

    /**
     * Starts the terminal UI event/render loop.
     * Mirrors Context.run().
     */
    fun run()

    /**
     * Sets the current background color for subsequent draw calls.
     */
    fun setBackgroundColor(color: VtColor)

    /**
     * Sets the current foreground color for subsequent draw calls.
     */
    fun setColor(color: VtColor)

    /**
     * Positions the cursor at (x, y).
     */
    fun setCursorPosition(x: Int, y: Int)

    /**
     * Sets the terminal window title.
     */
    fun setWindowTitle(title: String)

    /**
     * Appends text to the internal print buffer without flushing.
     */
    fun write(text: String)
}

enum class VtKeyType {
    Character,
    ArrowUp,
    ArrowDown,
    ArrowLeft,
    ArrowRight,
    Enter,
    Escape,
    Backspace,
    Tab,
    Unknown
}

data class VtKeyEvent(
    val type: VtKeyType,
    val ch: Char? = null,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false
)

enum class VtMouseButton {
    Left,
    Middle,
    Right,
    WheelUp,
    WheelDown,
    None
}

enum class VtMouseEventKind {
    Press,
    Release,
    Drag,
    Move
}

sealed class VtEvent {
    data class Key(val key: VtKeyEvent) : VtEvent()
    data class Mouse(
        val x: Int,
        val y: Int,
        val button: VtMouseButton,
        val kind: VtMouseEventKind,
        val ctrl: Boolean = false,
        val alt: Boolean = false,
        val shift: Boolean = false
    ) : VtEvent()
    data class Resize(val width: Int, val height: Int) : VtEvent()
}

fun interface VtEventListener {
    fun onEvent(ctx: VtDrawingContext, event: VtEvent)
}

/**
 * A VtDrawingContext that performs no I/O and has fixed zero-sized dimensions.
 */
class VoidVtDrawingContext : VtDrawingContext {

    override var onFrame: ((VtDrawingContext) -> Unit)? = null

    override val frameCount: Long
        get() = 0L

    override val windowWidth: Int
        get() = 0

    override val windowHeight: Int
        get() = 0

    override fun bold() {
        // no-op
    }

    override fun clear() {
        // no-op
    }

    override fun drawDashedLine(x1: Int, y1: Int, x2: Int, y2: Int) {
        // no-op
    }

    override fun drawEmptyDashedRect(x1: Int, y1: Int, x2: Int, y2: Int) {
        // no-op
    }

    override fun drawEmptyRect(x1: Int, y1: Int, x2: Int, y2: Int) {
        // no-op
    }

    override fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int) {
        // no-op
    }

    override fun drawPoint(x: Int, y: Int) {
        // no-op
    }

    override fun drawRect(x1: Int, y1: Int, x2: Int, y2: Int) {
        // no-op
    }

    override fun drawText(x: Int, y: Int, text: String) {
        // no-op
    }

    override fun flush() {
        // no-op
    }

    override fun hideCursor() {
        // no-op
    }

    override fun showCursor() {
        // no-op
    }

    override fun horizontalSeparator(y: Int) {
        // no-op
    }

    override fun reset() {
        // no-op
    }

    override fun resetBackgroundColor() {
        // no-op
    }

    override fun resetColor() {
        // no-op
    }

    override fun run() {
        // no-op
    }

    override fun setBackgroundColor(color: VtColor) {
        // no-op
    }

    override fun setColor(color: VtColor) {
        // no-op
    }

    override fun setCursorPosition(x: Int, y: Int) {
        // no-op
    }

    override fun setWindowTitle(title: String) {
        // no-op
    }

    override fun write(text: String) {
        // no-op
    }
}

class AnsiVtDrawingContext(
    private var listener: VtEventListener,
    private val targetFps: Int = 60,
    private val input: InputStream = System.`in`,
    private val output: PrintStream = System.out
) : VtDrawingContext {
    override var onFrame: ((VtDrawingContext) -> Unit)? = null
    fun setEventListener(l: VtEventListener) {
        this.listener = l
    }


    private val buffer = StringBuilder()

    private var _frameCount: Long = 0
    override val frameCount: Long
        get() = _frameCount

    private var _windowWidth: Int = 0
    private var _windowHeight: Int = 0
    override val windowWidth: Int
        get() = _windowWidth
    override val windowHeight: Int
        get() = _windowHeight

    private var running = false
    private var savedSttyState: String? = null

    // =============== Drawing API ===============

    override fun bold() {
        buffer.append("\u001B[1m")
    }

    override fun clear() {
        buffer.append("\u001B[2J\u001B[H")
    }

    override fun drawDashedLine(x1: Int, y1: Int, x2: Int, y2: Int) {
        // Simple horizontal/vertical dashed implementation
        if (y1 == y2) {
            val y = y1
            val start = minOf(x1, x2)
            val end = maxOf(x1, x2)
            for (x in start..end step 2) {
                setCursorPosition(x, y)
                buffer.append("-")
            }
        } else if (x1 == x2) {
            val x = x1
            val start = minOf(y1, y2)
            val end = maxOf(y1, y2)
            for (y in start..end step 2) {
                setCursorPosition(x, y)
                buffer.append("|")
            }
        } else {
            // Fallback: simple line
            drawLine(x1, y1, x2, y2)
        }
    }

    override fun drawEmptyDashedRect(x1: Int, y1: Int, x2: Int, y2: Int) {
        drawDashedLine(x1, y1, x2, y1)
        drawDashedLine(x1, y2, x2, y2)
        drawDashedLine(x1, y1, x1, y2)
        drawDashedLine(x2, y1, x2, y2)
    }

    override fun drawEmptyRect(x1: Int, y1: Int, x2: Int, y2: Int) {
        drawLine(x1, y1, x2, y1)
        drawLine(x1, y2, x2, y2)
        drawLine(x1, y1, x1, y2)
        drawLine(x2, y1, x2, y2)
    }

    override fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int) {
        // Bresenham-style integer line
        var x = x1
        var y = y1
        val dx = abs(x2 - x1)
        val dy = -abs(y2 - y1)
        val sx = if (x1 < x2) 1 else -1
        val sy = if (y1 < y2) 1 else -1
        var err = dx + dy

        while (true) {
            drawPoint(x, y)
            if (x == x2 && y == y2) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
    }

    override fun drawPoint(x: Int, y: Int) {
        setCursorPosition(x, y)
        buffer.append("█")
    }

    override fun drawRect(x1: Int, y1: Int, x2: Int, y2: Int) {
        val left = minOf(x1, x2)
        val right = maxOf(x1, x2)
        val top = minOf(y1, y2)
        val bottom = maxOf(y1, y2)

        for (yy in top..bottom) {
            setCursorPosition(left, yy)
            for (xx in left..right) {
                buffer.append(" ")
            }
        }
    }

    override fun drawText(x: Int, y: Int, text: String) {
        setCursorPosition(x, y)
        buffer.append(text)
    }

    override fun flush() {
        if (buffer.isNotEmpty()) {
            output.print(buffer.toString())
            output.flush()
            buffer.setLength(0)
        }
    }

    override fun hideCursor() {
        buffer.append("\u001B[?25l")
    }

    override fun showCursor() {
        buffer.append("\u001B[?25h")
    }

    override fun horizontalSeparator(y: Int) {
        val width = max(windowWidth, 1)
        setCursorPosition(0, y)
        repeat(width) { buffer.append("─") }
    }

    override fun reset() {
        buffer.append("\u001B[0m")
    }

    override fun resetBackgroundColor() {
        // 49 = default background
        buffer.append("\u001B[49m")
    }

    override fun resetColor() {
        // 39 = default foreground
        buffer.append("\u001B[39m")
    }

    override fun setBackgroundColor(color: VtColor) {
        buffer.append(color.background())
    }

    override fun setColor(color: VtColor) {
        buffer.append(color.foreground())
    }

    override fun setCursorPosition(x: Int, y: Int) {
        // Terminal is 1-based, our API is 0-based
        val col = x + 1
        val row = y + 1
        buffer.append("\u001B[${row};${col}H")
    }

    override fun setWindowTitle(title: String) {
        // OSC sequence: ESC ] 0 ; title BEL
        buffer.append("\u001B]0;${title}\u0007")
    }

    override fun write(text: String) {
        buffer.append(text)
    }

    // =============== Main loop / events ===============

    override fun run() {
        if (running) return
        running = true

        enterAlternateScreen()
        hideCursor()
        enableMouseTracking()
        enterRawMode()
        updateTerminalSize(forceEvent = true)
        flush()

        val frameDurationNanos = 1_000_000_000L / max(1, targetFps)

        try {
            while (running) {
                val frameStart = System.nanoTime()

                readAndDispatchInput()
                updateTerminalSize(forceEvent = false)

                // let the app render for this frame
                onFrame?.invoke(this)

                flush()
                _frameCount++

                val elapsed = System.nanoTime() - frameStart
                val remaining = frameDurationNanos - elapsed
                if (remaining > 0) {
                    val sleepMillis = remaining / 1_000_000L
                    val sleepNanos = (remaining % 1_000_000L).toInt()
                    try {
                        Thread.sleep(sleepMillis, sleepNanos)
                    } catch (_: InterruptedException) {
                        running = false
                    }
                }
            }
        } finally {
            exitRawMode()
            disableMouseTracking()
            showCursor()
            leaveAlternateScreen()
            reset()
            flush()
        }
    }

    fun stop() {
        running = false
    }

    // =============== Terminal setup helpers ===============

    private fun enterAlternateScreen() {
        // Switch to alternate screen buffer
        buffer.append("\u001B[?1049h")
    }

    private fun leaveAlternateScreen() {
        // Restore primary screen buffer
        buffer.append("\u001B[?1049l")
    }

    private fun enableMouseTracking() {
        // Enable SGR mouse reporting
        buffer.append("\u001B[?1002h") // button-event tracking
        buffer.append("\u001B[?1003h") // all-motion tracking
        buffer.append("\u001B[?1006h") // SGR extended mode
    }

    private fun disableMouseTracking() {
        buffer.append("\u001B[?1002l")
        buffer.append("\u001B[?1003l")
        buffer.append("\u001B[?1006l")
    }

    private fun enterRawMode() {
        // UNIX-only, requires /dev/tty
        savedSttyState = runCommand("sh", "-c", "stty -g < /dev/tty")?.trim()
        runCommand("sh", "-c", "stty raw -echo min 0 time 0 < /dev/tty")
    }

    private fun exitRawMode() {
        val state = savedSttyState ?: return
        runCommand("sh", "-c", "stty $state < /dev/tty")
    }

    private fun updateTerminalSize(forceEvent: Boolean) {
        val size = queryTerminalSize() ?: return
        val (cols, rows) = size
        if (cols != _windowWidth || rows != _windowHeight || forceEvent) {
            _windowWidth = cols
            _windowHeight = rows
            listener.onEvent(
                this,
                VtEvent.Resize(width = cols, height = rows)
            )
        }
    }

    private fun queryTerminalSize(): Pair<Int, Int>? {
        val out = runCommand("sh", "-c", "stty size < /dev/tty")?.trim()
            ?: return null
        if (out.isEmpty()) return null
        val parts = out.split(Regex("\\s+"))
        if (parts.size != 2) return null
        val rows = parts[0].toIntOrNull() ?: return null
        val cols = parts[1].toIntOrNull() ?: return null
        return cols to rows
    }

    private fun runCommand(vararg cmd: String): String? {
        return try {
            val proc = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    // =============== Input parsing ===============

    private fun readAndDispatchInput() {
        try {
            while (input.available() > 0) {
                val b = input.read()
                if (b < 0) return
                if (b == 0x1B) {
                    // ESC sequence
                    handleEscapeSequence()
                } else {
                    handleByteAsKey(b)
                }
            }
        } catch (_: Exception) {
            // ignore input errors for now
        }
    }

    private fun handleByteAsKey(b: Int) {
        val keyEvent = when (b) {
            0x0D, 0x0A -> VtKeyEvent(VtKeyType.Enter)
            0x7F, 0x08 -> VtKeyEvent(VtKeyType.Backspace)
            0x09 -> VtKeyEvent(VtKeyType.Tab)
            else -> {
                val ch = b.toChar()
                if (ch.isISOControl()) {
                    // crude ctrl detection for ASCII
                    val isCtrl = b in 1..26
                    VtKeyEvent(
                        type = VtKeyType.Unknown,
                        ch = null,
                        ctrl = isCtrl
                    )
                } else {
                    VtKeyEvent(
                        type = VtKeyType.Character,
                        ch = ch,
                        ctrl = false
                    )
                }
            }
        }
        listener.onEvent(this, VtEvent.Key(keyEvent))
    }

    private fun handleEscapeSequence() {
        // Read the rest of the sequence into a small buffer
        val seq = StringBuilder()
        // Small timeout-ish loop, do not block indefinitely
        while (input.available() > 0) {
            val b = input.read()
            if (b <= 0) break
            seq.append(b.toChar())
            // Rough heuristic: sequence ended on letter or tilde or m/M
            val c = b.toChar()
            if (c.isLetter() || c == '~') break
        }

        if (seq.isEmpty()) {
            // Bare ESC
            listener.onEvent(
                this,
                VtEvent.Key(VtKeyEvent(VtKeyType.Escape))
            )
            return
        }

        val s = seq.toString()
        if (s.startsWith("[")) {
            handleCsiSequence(s.substring(1))
        } else {
            // Alt+key style (ESC + char)
            val ch = s.last()
            listener.onEvent(
                this,
                VtEvent.Key(
                    VtKeyEvent(
                        type = VtKeyType.Character,
                        ch = ch,
                        alt = true
                    )
                )
            )
        }
    }

    private fun handleCsiSequence(body: String) {
        when (body) {
            "A" -> listener.onEvent(this, VtEvent.Key(VtKeyEvent(VtKeyType.ArrowUp)))
            "B" -> listener.onEvent(this, VtEvent.Key(VtKeyEvent(VtKeyType.ArrowDown)))
            "C" -> listener.onEvent(this, VtEvent.Key(VtKeyEvent(VtKeyType.ArrowRight)))
            "D" -> listener.onEvent(this, VtEvent.Key(VtKeyEvent(VtKeyType.ArrowLeft)))
            else -> {
                // Check for SGR mouse: <btn;x;yM or <btn;x;ym
                if (body.startsWith("<") && (body.endsWith("M") || body.endsWith("m"))) {
                    handleSgrMouse(body)
                } else {
                    // Ignore other CSI sequences for now
                }
            }
        }
    }

    private fun handleSgrMouse(body: String) {
        // body is like "<b;x;yM" or "<b;x;ym"
        val endsWithPress = body.endsWith("M")
        val withoutFinal = body.substring(0, body.length - 1)
        val parts = withoutFinal.removePrefix("<").split(";")
        if (parts.size != 3) return
        val btnCode = parts[0].toIntOrNull() ?: return
        val x = (parts[1].toIntOrNull() ?: return) - 1
        val y = (parts[2].toIntOrNull() ?: return) - 1

        val button = when {
            btnCode and 0b11 == 0 -> VtMouseButton.Left
            btnCode and 0b11 == 1 -> VtMouseButton.Middle
            btnCode and 0b11 == 2 -> VtMouseButton.Right
            btnCode and 0b111 == 64 -> VtMouseButton.WheelUp
            btnCode and 0b111 == 65 -> VtMouseButton.WheelDown
            else -> VtMouseButton.None
        }

        val shift = (btnCode and 4) != 0
        val alt   = (btnCode and 8) != 0
        val ctrl  = (btnCode and 16) != 0
        val motion = (btnCode and 32) != 0
        val noButton = (btnCode and 0b11) == 0b11

        val kind = when {
            motion && noButton -> VtMouseEventKind.Move
            motion -> VtMouseEventKind.Drag
            endsWithPress -> VtMouseEventKind.Press
            else -> VtMouseEventKind.Release
        }

        listener.onEvent(
            this,
            VtEvent.Mouse(
                x = x,
                y = y,
                button = button,
                kind = kind,
                ctrl = ctrl,
                alt = alt,
                shift = shift
            )
        )
    }
}

/**
 * Renders a DomNode tree into a VtDrawingContext and routes VtEvents to nodes.
 *
 * Layout model:
 *  - Node gets a rect by applying left/top/right/bottom margins relative to parent.
 *  - Missing margins default to 0.
 *  - The node fills the remaining space in parent after margins.
 */
class VtDomRenderer(
    private val ctx: VtDrawingContext,
    var root: DomNode
) : VtEventListener {

    private val hitList = mutableListOf<Pair<DomNode, Rect>>()
    // Keep routing drag/up events to the node that was pressed even if the cursor leaves its bounds
    private var mouseCapture: DomNode? = null
    private var focusedNode: DomNode? = null
    private var focusedId: String? = null

    fun frame() {
        hitList.clear()
        mouseCapture = null

        val rootRect = Rect(
            x = 0,
            y = 0,
            width = ctx.windowWidth,
            height = ctx.windowHeight
        )

        layoutNode(root, rootRect)
        focusedNode = focusedId?.let { id -> findById(root, id) }

        ctx.clear()
        drawNode(root)
        ctx.flush()
    }

    fun updateRoot(newRoot: DomNode) {
        root = newRoot
    }

    // Called by BoxComponent (and other components) to render children
    internal fun renderChildren(parent: DomNode) {
        for (child in parent.children) {
            drawNode(child)
        }
    }

    private fun drawNode(node: DomNode) {
        val comp = node.component ?: BoxComponent
        comp.render(node, ctx, this)
    }

    // ========== LAYOUT ==========

    private fun layoutNode(node: DomNode, parentRect: Rect) {
        val l = node.style.left ?: 0
        val t = node.style.top ?: 0
        val r = node.style.right ?: (parentRect.width - 1)
        val b = node.style.bottom ?: (parentRect.height - 1)

        // Position in absolute screen coords
        val x = parentRect.x + l
        val y = parentRect.y + t

        // Raw size from box coords
        var width = (r - l + 1).coerceAtLeast(0)
        var height = (b - t + 1).coerceAtLeast(0)

        // Clamp to parent rect so we don't overflow
        val maxWidthInParent = (parentRect.width - l).coerceAtLeast(0)
        val maxHeightInParent = (parentRect.height - t).coerceAtLeast(0)
        width = width.coerceAtMost(maxWidthInParent)
        height = height.coerceAtMost(maxHeightInParent)

        val rect = Rect(x, y, width, height)
        node.bounds = rect
        hitList += node to rect

        // Children layout inside this node's rect
        for (child in node.children) {
            layoutNode(child, rect)
        }
    }


    // ========== EVENTS ==========

    override fun onEvent(ctx: VtDrawingContext, event: VtEvent) {
        when (event) {
            is VtEvent.Mouse  -> handleMouse(event)
            is VtEvent.Key    -> handleKey(event)
            is VtEvent.Resize -> handleResize(event)
        }
    }

    private fun handleMouse(ev: VtEvent.Mouse) {
        val hit = hitList.lastOrNull { (_, rect) -> rect.contains(ev.x, ev.y) }
        val target = when (ev.kind) {
            VtMouseEventKind.Press -> hit?.first
            VtMouseEventKind.Release,
            VtMouseEventKind.Drag -> mouseCapture ?: hit?.first
            VtMouseEventKind.Move -> mouseCapture ?: hit?.first
        } ?: return

        // Prefer the stored rect for the target (capture) even if the pointer left its bounds
        val rect = hitList.firstOrNull { (node, _) -> node == target }?.second ?: hit?.second ?: return

        val localX = ev.x - rect.x
        val localY = ev.y - rect.y

        val domEv = DomMouseEvent(
            localX = localX,
            localY = localY,
            globalX = ev.x,
            globalY = ev.y,
            original = ev
        )

        when (ev.kind) {
            VtMouseEventKind.Press -> {
                mouseCapture = target
                focusedNode = target
                focusedId = target.id
                target.onMouseDown?.invoke(domEv)
            }
            VtMouseEventKind.Release -> {
                target.onMouseUp?.invoke(domEv)
                mouseCapture = null
            }
            VtMouseEventKind.Move,
            VtMouseEventKind.Drag -> target.onMouseMove?.invoke(domEv)
        }
    }

    private fun handleKey(ev: VtEvent.Key) {
        val target = focusedNode ?: root
        target.onKey?.invoke(DomKeyEvent(ev.key, ev))
    }

    private fun handleResize(ev: VtEvent.Resize) {
        val domEv = DomResizeEvent(ev.width, ev.height, ev)
        broadcastResize(root, domEv)
    }

    private fun broadcastResize(node: DomNode, ev: DomResizeEvent) {
        node.onWindowResize?.invoke(ev)
        for (child in node.children) {
            broadcastResize(child, ev)
        }
    }

    private fun findById(node: DomNode, id: String): DomNode? {
        if (node.id == id) return node
        for (child in node.children) {
            val found = findById(child, id)
            if (found != null) return found
        }
        return null
    }
}

data class DomMouseEvent(
    val localX: Int,
    val localY: Int,
    val globalX: Int,
    val globalY: Int,
    val original: VtEvent.Mouse
)

data class DomKeyEvent(
    val key: VtKeyEvent,
    val original: VtEvent.Key
)

data class DomResizeEvent(
    val width: Int,
    val height: Int,
    val original: VtEvent.Resize
)

data class Rect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    fun contains(px: Int, py: Int): Boolean =
        px >= x && py >= y && px < x + width && py < y + height
}

/**
 * Slim DOM node.
 */
class DomNode(
    val id: String? = null,
    var text: String = "",
    var style: StyleSet = StyleSet(),
    var component: DomComponent? = null,
    var onMouseDown: ((DomMouseEvent) -> Unit)? = null,
    var onMouseUp: ((DomMouseEvent) -> Unit)? = null,
    var onMouseMove: ((DomMouseEvent) -> Unit)? = null,
    var onKey: ((DomKeyEvent) -> Unit)? = null,
    var onWindowResize: ((DomResizeEvent) -> Unit)? = null
) {
    var parent: DomNode? = null
        internal set

    val children: MutableList<DomNode> = mutableListOf()

    internal var bounds: Rect = Rect(0, 0, 0, 0)

    fun addChild(child: DomNode): DomNode {
        child.parent = this
        children += child
        return child
    }
}

interface DomComponent {
    /**
     * Render this node.
     * Layout (node.bounds) is already computed by DomRenderer.
     */
    fun render(node: DomNode, ctx: VtDrawingContext, renderer: VtDomRenderer)
}

object BoxComponent : DomComponent {

    override fun render(node: DomNode, ctx: VtDrawingContext, renderer: VtDomRenderer) {
        val rect = node.bounds
        if (rect.width <= 0 || rect.height <= 0) return

        applyStyle(ctx, node.style)

        // Fill background if any
        if (node.style.bg != null && rect.width > 0 && rect.height > 0) {
            for (yy in rect.y until rect.y + rect.height) {
                ctx.setCursorPosition(rect.x, yy)
                var remaining = rect.width
                while (remaining > 0) {
                    val chunk = minOf(remaining, 64)
                    ctx.write(" ".repeat(chunk))
                    remaining -= chunk
                }
            }
        }

        // Draw text (clipped to rect)
        if (node.text.isNotEmpty()) {
            val lines = node.text.split('\n')
            var y = rect.y
            for (line in lines) {
                if (y >= rect.y + rect.height) break
                if (rect.width <= 0) break
                val clipped = if (line.length > rect.width) {
                    line.substring(0, rect.width)
                } else {
                    line
                }
                ctx.drawText(rect.x, y, clipped)
                y++
            }
        }

        // Draw children on top
        renderer.renderChildren(node)
    }

    private fun applyStyle(ctx: VtDrawingContext, style: StyleSet) {
        ctx.reset()
        style.fg?.let { ctx.setColor(it) } ?: ctx.resetColor()
        style.bg?.let { ctx.setBackgroundColor(it) } ?: ctx.resetBackgroundColor()
        val deco = style.textDecoration?.lowercase()
        if (deco?.contains("bold") == true) ctx.bold()
        if (deco?.contains("italic") == true) ctx.write("\u001B[3m")
    }
}

fun Splitter(
    state: AppState,
    style: StyleSet
): DomNode {
    val t = style.top ?: 0
    val b = style.bottom ?: t
    val height = (b - t + 1).coerceAtLeast(1)
    return DomNode(
        id = "splitter",
        style=style,
        component = BoxComponent,
        text = "│\n".repeat(height),
        onMouseDown = { e ->
            state.dragging = true
            state.dragStartMouseX = e.globalX
            state.dragStartSplitterPosX = state.splitterPosX
            state.statusText = "Splitter click ${e.globalX},${e.globalY}->${state}"
        },
        onMouseMove = { e ->
            if (state.dragging) {
                val dx = e.globalX - state.dragStartMouseX
                state.splitterPosX = state.dragStartSplitterPosX + dx
                state.statusText = "Splitter dragging ${e.globalX},${e.globalY}->${state}"
            }
        },
        onMouseUp = { e ->
            val dx = e.globalX - state.dragStartMouseX
            state.splitterPosX = state.dragStartSplitterPosX + dx
            state.dragging = false
            state.statusText = "Splitter finished ${e.globalX},${e.globalY}->${state}"
        }
    )
}

private const val MIN_PANEL_WIDTH = 40

data class AppState(
    var splitterPosX: Int = 40,
    var dragging: Boolean = false,
    var dragStartMouseX: Int = 0,
    var dragStartSplitterPosX: Int = 40,
    var statusText: String = ""
){
    override fun toString():String {
        return """sW:$splitterPosX,drg:$dragging,dSmX:$dragStartMouseX,dSsW:$dragStartSplitterPosX"""
    }
}

class App(private val ctx: AnsiVtDrawingContext) : VtEventListener {

    private var state = AppState()
    private var renderer: VtDomRenderer? = null
    private var running = true
    private val stateStore = StateStore()

    init {
        ctx.setEventListener(this)         // <-- use the generic setter
        ctx.onFrame = { frame() }
    }

    override fun onEvent(ctx: VtDrawingContext, event: VtEvent) {
        // global shortcuts
        if (event is VtEvent.Key &&
            event.key.type == VtKeyType.Character &&
            event.key.ch == 'q') {
            running = false
            this.ctx.stop()
            return
        }

        // let DOM event handlers run
        renderer?.onEvent(ctx, event)
    }
    fun appView(
        state: AppState,
        ctx: VtDrawingContext,
        store: StateStore
    ): DomNode {
        val root = DomNode(
            id = "root",
            component = BoxComponent,
            style = StyleSet.parse("left:0; top:0; right:${ctx.windowWidth - 1}; bottom:${ctx.windowHeight - 1}")
        )

        val w = ctx.windowWidth - 4
        val h = ctx.windowHeight - 4

        // main horizontal layout from state
        val minPanelWidth = MIN_PANEL_WIDTH
        val maxPanelWidth = (w - 2).coerceAtLeast(minPanelWidth)
        val panelWidth = state.splitterPosX.coerceIn(minPanelWidth, maxPanelWidth)

        val mainHeight = h - 2

        // components
        val titleBar = DomNode(
            id = "title",
            style = StyleSet.parse("left:0; top:0; right:$w; bottom:0; fg:#bebebb; bg:#223388"),
            component = BoxComponent,
            text = " TUI Demo (q = quit) ",
            onMouseMove = { e ->
                state.statusText = "Title hovered ${e.globalX},${e.globalY}->${state}"
            }
        )

        val main = DomNode(
            id = "main",
            style = StyleSet.parse("left:0; top:1; right:$w; bottom:${h - 1}; fg:#bebebb; bg:#182460"),
            component = BoxComponent,
            onMouseMove = { e ->
                state.statusText = "main hovered ${e.globalX},${e.globalY}->${state}"
            }
        )

        val statusBar = DomNode(
            id = "status",
            style = StyleSet.parse("left:0; top:${h - 1}; right:$w; bottom:${h - 1}; fg:#bebebb; bg:#4d4d4d"),
            component = BoxComponent,
            text = "${state.statusText} | split=${panelWidth} drag=${state.dragging}",
            onMouseMove = { e ->
                state.statusText = "status hovered ${e.globalX},${e.globalY}->${state}"
            }
        )

        val fileTree = FileTreePanel(
            state,
            StyleSet.parse("left:0; top:0; right:${panelWidth - 1}; bottom:${mainHeight - 1}; fg:#bebebb; bg:#636fab"),
            store
        )
        val splitter = Splitter(
            state,
            StyleSet.parse("left:$panelWidth; top:0; right:$panelWidth; bottom:$mainHeight; fg:#bebebb; bg:#808080")
        )
        val editor = EditorPanel(
            state,
            StyleSet.parse("left:${panelWidth + 1}; top:0; right:$w; bottom:$mainHeight; fg:#bebebb; bg:#182460"),
            store
        )

        root.addChild(titleBar)
        root.addChild(main.apply {
            addChild(fileTree)
            addChild(splitter)
            addChild(editor)
        })
        root.addChild(statusBar)

        return root
    }
    fun frame() {
        if (!running) return

        val root = appView(state, ctx, stateStore)
        if (renderer == null) {
            renderer = VtDomRenderer(ctx, root)
        } else {
            renderer!!.updateRoot(root)
        }
        renderer!!.frame()
    }
}

fun FileTreePanel(
    state: AppState,
    style: StyleSet,
    store: StateStore
): DomNode {
    val node = DomNode(
        id = "file-tree",
        style=style,
        component = BoxComponent,
        text = "File tree\n[placeholder]"
    )
    val (clicks, setClicks) = store.use(node, "clicks") { 0 }
    node.text = "File tree\n[placeholder]\nclicks:$clicks"
    node.onMouseDown = { e ->
        setClicks(clicks + 1)
        state.statusText = "FileTree click ${e.globalX},${e.globalY}->${state}"
    }
    node.onMouseMove = { e ->
        state.statusText = "FileTree hovered ${e.globalX},${e.globalY}->${state}"
    }
    return node
}

fun EditorPanel(
    state: AppState,
    style: StyleSet,
    store: StateStore
): DomNode {
    val node = DomNode(
        id = "editor",
        style = style,
        component = BoxComponent,
        text = "Editor\n[placeholder]"
    )
    val (hovers, setHovers) = store.use(node, "hovers") { 0 }
    node.text = "Editor\n[placeholder]\nhovers:$hovers"
    node.onMouseDown = { e ->
        state.statusText = "Editor click ${e.globalX},${e.globalY}->${state}"
    }
    node.onMouseMove = { e ->
        setHovers(hovers + 1)
        state.statusText = "Editor hovered ${e.globalX},${e.globalY}->${state}"
    }
    return node
}
