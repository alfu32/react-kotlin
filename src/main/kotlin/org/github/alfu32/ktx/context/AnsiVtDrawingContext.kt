package org.github.alfu32.ktx.context

import org.github.alfu32.ktx.color.VtColor
import java.io.InputStream
import java.io.PrintStream
import kotlin.math.max

class AnsiVtDrawingContext(
    private val listener: VtEventListener,
    private val targetFps: Int = 60,
    private val input: InputStream = System.`in`,
    private val output: PrintStream = System.out
) : VtDrawingContext {

    override var onFrame: ((VtDrawingContext) -> Unit)? = null

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
        val dx = kotlin.math.abs(x2 - x1)
        val dy = -kotlin.math.abs(y2 - y1)
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

                // Process input -> events
                readAndDispatchInput()

                // Detect resize
                updateTerminalSize(forceEvent = false)

                // Let DOM (or any client) draw:
                onFrame?.invoke(this)

                // User code is expected to have drawn into buffer by now
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
        buffer.append("\u001B[?1006h") // SGR extended mode
    }

    private fun disableMouseTracking() {
        buffer.append("\u001B[?1002l")
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
        val isPressOrDrag = body.endsWith("M")
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

        val kind = when {
            motion && isPressOrDrag -> VtMouseEventKind.Drag
            motion && !isPressOrDrag -> VtMouseEventKind.Move
            isPressOrDrag -> VtMouseEventKind.Press
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
