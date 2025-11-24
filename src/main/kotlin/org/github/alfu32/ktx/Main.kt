package org.github.alfu32.ktx

import java.io.InputStream
import java.io.Flushable
import java.lang.ProcessBuilder
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter


/*
===============================================================
  FILE TREE INTERFACE
===============================================================
  Describes the required API for a generic line-based text buffer
  abstraction, independent of UI toolkit or rendering layer.
===============================================================
*/
interface IFileTree {
    val root: String

    fun toggle(path: String)
    fun flattened(): List<FileTreeEntry>
    fun refreshOpenNodes()
}
/*
===============================================================
  FILE TREE INTERFACE DATA TYPES
===============================================================
*/
data class FileTreeEntry(
    val name: String,
    val typ: String,      // "file" or "folder"
    val padding: Int,
    val fullPath: String,
    val isOpen: Boolean
)

class FileTreeItem(
    val name: String,
    val fullPath: String,
    val isDir: Boolean,
    var isOpen: Boolean = false,
    var children: List<String> = emptyList()
)

/*
   ===============================================================
     TEXT BUFFER INTERFACE
   ===============================================================
     Describes the required API for a generic line-based text buffer
     abstraction, independent of UI toolkit or rendering layer.
   ===============================================================
   */
interface ITextBuffer {

    fun text(): String
    fun clone(): ITextBuffer

    fun cursorPosition(): Position
    fun selectionText(): String
    fun totalLines(): Int
    fun bom(): String
    fun encoding(): String

    fun loadText(text: String)

    fun moveCursorTo(position: Position, expand: Boolean)
    fun moveLeft(expand: Boolean = false, word: Boolean = false)
    fun moveRight(expand: Boolean = false, word: Boolean = false)
    fun moveUp(expand: Boolean = false)
    fun moveDown(expand: Boolean = false)
    fun moveStartOfLine(expand: Boolean = false)
    fun moveEndOfLine(expand: Boolean = false)
    fun selectAll()

    fun insertText(text: String)
    fun insertNewline()

    fun deleteBackspace()
    fun deleteForward()

    fun copySelection(): Boolean
    fun cutSelection(): Boolean
    fun pasteClipboard()

    fun consumeNotifications(): List<Notification>

    fun startSelection(pos: Position)
    fun selectTo(pos: Position)
    fun hasSelection(): Boolean
    fun clearSelection()

    fun viewportSlice(view: EditorViewport, gutterWidth: Int): ViewportSlice
}

data class Position(var line: Int = 0, var column: Int = 0)

data class SelectionRange(val start: Position, val end: Position)

enum class NotificationKind { COPY, CUT }

data class Notification(val kind: NotificationKind, val text: String)

data class EditorViewport(val x: Int, val y: Int, val width: Int, val height: Int)

data class ViewSegment(val text: String, val selected: Boolean)

data class ViewLine(val lineIndex: Int, val gutter: String, val segments: List<ViewSegment>)

data class CursorView(val line: Int, val column: Int, val char: String)

data class ViewportSlice(val lines: List<ViewLine>, val totalLines: Int, val cursor: CursorView?)

data class EditorState(
    val filePath: String,
    val language: String,
    val cursorLine: Int,
    val cursorColumn: Int,
    val selection: String,
    val totalLines: Int,
    val bom: String,
    val encoding: String
)

/*
===============================================================
  DATA TYPES
===============================================================
*/


/*
===============================================================
  TEXT BUFFER IMPLEMENTATION
===============================================================
*/
//@file:Suppress("UNCHECKED_CAST")

/* =====================================================================
   UNIFIED EVENT STRUCTURE
   ===================================================================== */

data class UIEvent(
    val kind: String,           // e.g. "mouse_down", "key_down", "resize"
    val x: Int? = null,         // mouse coordinates
    val y: Int? = null,
    val relX: Int? = null,         // mouse coordinates
    val relY: Int? = null,
    val button: Int? = null,    // mouse button
    val scrollDelta: Int? = null,
    val key: String? = null,    // keyboard key
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
    val focusId: String? = null,
    val cols: Int? = null,      // resize cols
    val rows: Int? = null,       // resize rows
    val raw: String = ""       // resize rows
){
    fun alterCopy(conf:UIEvent)= UIEvent(
            kind= this.kind,
            x= conf.x ?: this.x,
            y= conf.y ?: this.y,
            relX= conf.relX ?: this.relX,
            relY= conf.relY ?: this.relY,
            button= conf.button ?: this.button,
            scrollDelta= conf.scrollDelta ?: this.scrollDelta,
            key= conf.key ?: this.key,
            ctrl = conf.ctrl || this.ctrl,
            alt = conf.alt || this.alt,
            shift = conf.shift || this.shift,
            meta = conf.meta || this.meta,
            focusId= conf.focusId ?: this.focusId,
            cols= conf.cols ?: this.cols,
            rows= conf.rows ?: this.rows,
        )
}

/* =====================================================================
   STYLE SYSTEM (as provided by you)
   ===================================================================== */

data class Color(val r: Int, val g: Int, val b: Int)

data class StyleSet(
    var top: Int? = null,
    var left: Int? = null,
    var bottom: Int? = null,
    var right: Int? = null,
    var bg: Color? = null,
    var fg: Color? = null,
    var textDecoration: String? = null,
    var borderSet: String? = null,
    var lineSet: String? = null
) {
    fun mergeFrom(src: StyleSet) {
        if (src.top != null) top = src.top
        if (src.left != null) left = src.left
        if (src.bottom != null) bottom = src.bottom
        if (src.right != null) right = src.right
        if (src.bg != null) bg = src.bg
        if (src.fg != null) fg = src.fg
        if (src.textDecoration != null) textDecoration = src.textDecoration
        if (src.borderSet != null) borderSet = src.borderSet
        if (src.lineSet != null) lineSet = src.lineSet
    }

    fun merged(src: StyleSet): StyleSet =
        this.copy().also { it.mergeFrom(src) }

    companion object {
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
                }
            }
            return style
        }

        private fun parseColor(raw: String): Color? {
            var s = raw.trim()
            if (s.startsWith("#")) s = s.substring(1)
            if (s.length != 6) return null
            val r = s.substring(0, 2).toIntOrNull(16) ?: return null
            val g = s.substring(2, 4).toIntOrNull(16) ?: return null
            val b = s.substring(4, 6).toIntOrNull(16) ?: return null
            return Color(r, g, b)
        }
    }
}

class StyleSheet(
    val defaultStyle: StyleSet = StyleSet(),
    val rules: Map<String, StyleSet> = emptyMap()
) {

    fun getStyle(styleId: String): StyleSet {
        val matchingKeys = rules.keys.filter { key ->
            val path = splitPathKey(key)
            path.isNotEmpty() && path.last() == styleId
        }
        if (matchingKeys.isEmpty()) return defaultStyle.copy()

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
        fun loadFromFiles(files: List<String>): StyleSheet {
            val aggregateDefault = StyleSet()
            val aggregateRules = mutableMapOf<String, StyleSet>()

            for (path in files) {
                val css = try {
                    File(path).takeIf { it.exists() }?.readText()
                } catch (_: Exception) { null } ?: continue

                val sheet = parse(css)
                aggregateDefault.mergeFrom(sheet.defaultStyle)
                for ((k, v) in sheet.rules) {
                    val target = aggregateRules.getOrPut(k) { StyleSet() }
                    target.mergeFrom(v)
                }
            }

            return StyleSheet(aggregateDefault, aggregateRules)
        }

        fun parse(css: String): StyleSheet {
            val defaultStyle = StyleSet()
            val rules = mutableMapOf<String, StyleSet>()

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
                    val segments = selectorRaw
                        .replace(">", " ")
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }

                    if (segments.isNotEmpty()) {
                        val key = segments.joinToString(".")
                        val existing = rules.getOrPut(key) { StyleSet() }
                        existing.mergeFrom(style)
                    }
                }
            }

            return StyleSheet(defaultStyle, rules)
        }

        private fun splitPathKey(key: String): List<String> =
            if (key.isBlank()) emptyList() else key.split('.')
    }
}

private fun applyStyles(dom: DOMNode, sheet: StyleSheet?): DOMNode {
    val resolvedStyle = StyleSet()
    if (sheet != null) {
        // Base styles by id then tag
        dom.id?.let { resolvedStyle.mergeFrom(sheet.getStyle(it)) }
        resolvedStyle.mergeFrom(sheet.getStyle(dom.tag))
        // Focus styles
        if (dom.hasFocus) {
            dom.id?.let { resolvedStyle.mergeFrom(sheet.getStyle("$it:focus")) }
            resolvedStyle.mergeFrom(sheet.getStyle("${dom.tag}:focus"))
        }
    }
    // Inline style overrides everything else
    resolvedStyle.mergeFrom(dom.style)

    val styledChildren = dom.children.map { applyStyles(it, sheet) }
    return dom.copy(style = resolvedStyle, children = styledChildren)
}

/* =====================================================================
   CANVAS RENDERER INTERFACE WITH EVENT POLLING
   ===================================================================== */

interface CanvasRenderer {
    fun cols(): Int
    fun rows(): Int
    fun clear()
    fun setColor(r: Int, g: Int, b: Int)
    fun setBackgroundColor(r: Int, g: Int, b: Int)
    fun bold(enabled: Boolean)
    fun italic(enabled: Boolean)
    fun underline(enabled: Boolean)
    fun blink(enabled: Boolean)
    fun drawRect(x: Int, y: Int, width: Int, height: Int)
    fun drawText(x: Int, y: Int, text: String)
    fun setCursorPosition(x: Int, y: Int)
    fun flush()

    // Event API
    fun pollEvent(): UIEvent?
    fun tryPollEvent(): UIEvent?

    /* ============================================================
   Lifecycle / Terminal Control
   ============================================================ */
    fun enableMouseTracking()
    fun disableMouseTracking()

    fun hideCursor()
    fun showCursor()
    fun resetAttributes()

    fun isRunning(): Boolean
    fun requestExit()
    fun shutdown()
}

/* =====================================================================
   Minimal no-op renderer to make the main loop runnable without a real VT backend.
   ===================================================================== */

class NoopRenderer(
    private val cols: Int = 120,
    private val rows: Int = 40
) : CanvasRenderer {
    override fun cols(): Int = cols
    override fun rows(): Int = rows
    override fun clear() {}
    override fun setColor(r: Int, g: Int, b: Int) {}
    override fun setBackgroundColor(r: Int, g: Int, b: Int) {}
    override fun bold(enabled: Boolean) {}
    override fun italic(enabled: Boolean) {}
    override fun underline(enabled: Boolean) {}
    override fun blink(enabled: Boolean) {}
    override fun drawRect(x: Int, y: Int, width: Int, height: Int) {}
    override fun drawText(x: Int, y: Int, text: String) {}
    override fun setCursorPosition(x: Int, y: Int) {}
    override fun flush() {}
    override fun pollEvent(): UIEvent? = null
    override fun tryPollEvent(): UIEvent? = null

    /* ============================================================
   Lifecycle / Terminal Control (no-op)
   ============================================================ */

    override fun enableMouseTracking() {}
    override fun disableMouseTracking() {}
    override fun hideCursor() {}
    override fun showCursor() {}
    override fun resetAttributes() {}
    @Volatile
    private var running = true

    override fun isRunning(): Boolean = running
    override fun requestExit() { running = false }

    override fun shutdown() {}
}

/* =====================================================================
   ANSI Terminal Renderer

This renderer:

 - Uses ANSI escape sequences
 - Assumes raw mode is enabled (you’ll handle this outside—Termux/Linux)
 - Reads stdin for key and mouse events
 - Supports SGR text formatting
 - Supports RGB foreground/background
 - Draws rectangles and text
 - Maintains no back buffer (your framework controls redraw)

Note: Terminal mouse reporting requires enabling Mouse Tracking Mode.
You’ll need to enable it once, outside this class:

```
    print("\u001b[?1000h") // Mouse tracking on (press/release)
    print("\u001b[?1003h") // Mouse motion tracking
```

    And raw mode for stdin.
   ===================================================================== */

class AnsiCanvasRenderer(
    private val input: InputStream = System.`in`,
    private val output: Appendable = System.out,
    private val initialCols: Int = 200,
    private val initialRows: Int = 80
) : CanvasRenderer {

    @Volatile
    private var currentCols: Int = initialCols
    @Volatile
    private var currentRows: Int = initialRows
    @Volatile
    private var pendingResize: UIEvent? = null
    private var lastSizeCheckNanos: Long = 0L

    private val frame = StringBuilder()

    init {
        queryTerminalSize()?.let { (rows, cols) ->
            currentRows = rows
            currentCols = cols
        }
    }

    private fun esc(code: String) {
        frame.append("\u001b[$code")
    }

    override fun cols(): Int = currentCols
    override fun rows(): Int = currentRows

    /* ============================================================
       Drawing API
       ============================================================ */

    override fun clear() {
        esc("2J")      // clear
        esc("H")       // cursor home
    }

    override fun setColor(r: Int, g: Int, b: Int) {
        esc("38;2;$r;$g;${b}m")
    }

    override fun setBackgroundColor(r: Int, g: Int, b: Int) {
        esc("48;2;$r;$g;${b}m")
    }

    override fun bold(enabled: Boolean) {
        esc(if (enabled) "1m" else "22m")
    }

    override fun italic(enabled: Boolean) {
        esc(if (enabled) "3m" else "23m")
    }

    override fun underline(enabled: Boolean) {
        esc(if (enabled) "4m" else "24m")
    }

    override fun blink(enabled: Boolean) {
        esc(if (enabled) "5m" else "25m")
    }

    override fun drawRect(x: Int, y: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        for (row in 0 until height) {
            esc("${y + row + 1};${x + 1}H")
            repeat(width) { frame.append(" ") }
        }
    }

    override fun drawText(x: Int, y: Int, text: String) {
        esc("${y + 1};${x + 1}H")
        frame.append(text)
    }

    override fun setCursorPosition(x: Int, y: Int) {
        esc("${y + 1};${x + 1}H")
    }

    override fun flush() {
        output.append(frame.toString())
        if (output is Flushable) {
            (output as Flushable).flush()
        }
        frame.setLength(0)
    }

    /* ============================================================
       Event Parsing
       ============================================================ */

    private fun queryTerminalSize(): Pair<Int, Int>? {
        val output = runCommand("sh", "-c", "stty size < /dev/tty")?.trim() ?: return null
        val parts = output.split(Regex("\\s+"))
        if (parts.size != 2) return null
        val rows = parts[0].toIntOrNull() ?: return null
        val cols = parts[1].toIntOrNull() ?: return null
        return rows to cols
    }

    private fun checkForResizeEvent() {
        val now = System.nanoTime()
        if (now - lastSizeCheckNanos < 200_000_000L) return // throttle checks (~5/sec)
        lastSizeCheckNanos = now

        val (rows, cols) = queryTerminalSize() ?: return
        if (rows != currentRows || cols != currentCols) {
            currentRows = rows
            currentCols = cols
            pendingResize = UIEvent("resize", cols = currentCols, rows = currentRows)
        }
    }

    override fun pollEvent(): UIEvent? {
        while (true) {
            val e = tryPollEvent()
            if (e != null) return e
            Thread.sleep(5)
        }
    }

    override fun tryPollEvent(): UIEvent? {
        checkForResizeEvent()
        pendingResize?.let {
            pendingResize = null
            return it
        }
        if (input.available() <= 0) return null

        val b = input.read()
        if (b < 0) return null

        return parseAnsiInput(b)
    }

    private fun parseAnsiInput(firstByte: Int): UIEvent? {
        // Handle ESC sequences
        if (firstByte == 0x1b) {
            val next = input.read()
            if (next == '['.code) {
                return parseCsi()
            }
            // Alt-modified char: ESC + char
            val ch = next.toChar()
            return UIEvent(kind="key_down", key="$ch", alt = true)
        }

        // Control keys
        when (firstByte) {
            0x7F, 0x08 -> return UIEvent(kind="key_down", key="Backspace")
            0x0D, 0x0A -> return UIEvent(kind="key_down", key="Enter")
        }

        // Simple printable/control chars (use ctrl flag for ASCII control range)
        val ch = firstByte.toChar()
        val isCtrl = firstByte in 1..26
        val keyName = if (isCtrl) ch.plus(64).toChar().toString() else "$ch"
        return UIEvent(kind="key_down", key=keyName, ctrl = isCtrl)
    }

    private fun parseCsi(): UIEvent? {
        val seq = StringBuilder()
        while (input.available() > 0) {
            val c = input.read().toChar()
            seq.append(c)
            if ((c in 'A'..'Z') || (c in 'a'..'z')) break
        }
        val s = seq.toString()
        val finalChar = s.lastOrNull() ?: return null
        val body = s.dropLast(1)
        val params = if (body.isEmpty()) emptyList() else body.split(';')

        data class Mods(val shift: Boolean, val alt: Boolean, val ctrl: Boolean, val meta: Boolean)
        fun decodeMods(modParam: Int): Mods {
            // xterm modifier encoding: mod = 1 + (shift?1) + (alt?2) + (ctrl?4) + (meta?8)
            val bits = (modParam - 1).coerceAtLeast(0)
            val shift = (bits and 1) != 0
            val alt = (bits and 2) != 0
            val ctrl = (bits and 4) != 0
            val meta = (bits and 8) != 0
            return Mods(shift, alt, ctrl, meta)
        }

        // Mouse SGR: <btn;x;yM or <btn;x;ym
        if ((s.endsWith("M") || s.endsWith("m")) && s.startsWith("<")) {
            val parts = s.dropLast(1).split(';')
            if (parts.size >= 3) {
                val btnCode = parts[0].drop(1).toIntOrNull() ?: return null
                val x = parts[1].toIntOrNull()?.minus(1) ?: return null
                val y = parts[2].toIntOrNull()?.minus(1) ?: return null
                val press = s.endsWith("M")
                val motion = (btnCode and 32) != 0
                val baseBtn = btnCode and 0b11
                val scroll = btnCode and 0b111

                val shift = (btnCode and 4) != 0
                val alt = (btnCode and 8) != 0
                val ctrl = (btnCode and 16) != 0

                // Scroll wheel
                if (scroll == 64) return UIEvent("mouse_scroll", x = x, y = y, scrollDelta = 1, ctrl = ctrl, alt = alt, shift = shift)
                if (scroll == 65) return UIEvent("mouse_scroll", x = x, y = y, scrollDelta = -1, ctrl = ctrl, alt = alt, shift = shift)

                val button = when (baseBtn) {
                    0 -> 0
                    1 -> 1
                    2 -> 2
                    else -> null
                }

                val kind = when {
                    motion -> "mouse_move"      // treat any motion as move
                    press -> "mouse_down"
                    else -> "mouse_up"
                }
                return UIEvent(kind, x = x, y = y, button = button, ctrl = ctrl, alt = alt, shift = shift)
            }
        }

        // Keys / navigation with optional modifiers (CSI 1;5A, etc.)
        val mods = if (params.size >= 2) {
            decodeMods(params.last().toIntOrNull() ?: 1)
        } else Mods(false, false, false, false)

        return when (finalChar) {
            'A' -> UIEvent("key_down", key="Up", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
            'B' -> UIEvent("key_down", key="Down", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
            'C' -> UIEvent("key_down", key="Right", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
            'D' -> UIEvent("key_down", key="Left", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
            'H' -> UIEvent("key_down", key="Home", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
            'F' -> UIEvent("key_down", key="End", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
            '~' -> {
                val code = params.firstOrNull()?.toIntOrNull()
                when (code) {
                    1, 7 -> UIEvent("key_down", key="Home", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
                    4, 8 -> UIEvent("key_down", key="End", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
                    2 -> UIEvent("key_down", key="Insert", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
                    3 -> UIEvent("key_down", key="Delete", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
                    5 -> UIEvent("key_down", key="PageUp", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
                    6 -> UIEvent("key_down", key="PageDown", shift = mods.shift, alt = mods.alt, ctrl = mods.ctrl, meta = mods.meta)
                    else -> null
                }
            }
            else -> null
        }
    }

    /* ============================================================
   Lifecycle / Terminal Control
   ============================================================ */

    // Enable terminal mouse tracking modes
    override fun enableMouseTracking() {
        // Basic click, drag & motion, SGR (extended coords)
        output.append("\u001b[?1000h") // mouse click
        output.append("\u001b[?1002h") // mouse drag
        output.append("\u001b[?1003h") // mouse motion
        output.append("\u001b[?1006h") // SGR extended
    }

    // Disable all mouse modes
    override fun disableMouseTracking() {
        output.append("\u001b[?1000l")
        output.append("\u001b[?1002l")
        output.append("\u001b[?1003l")
        output.append("\u001b[?1006l")
    }

    // Cursor visibility: hide/show
    override fun hideCursor() {
        output.append("\u001b[?25l")
    }

    override fun showCursor() {
        output.append("\u001b[?25h")
    }

    // Reset SGR attributes
    override fun resetAttributes() {
        output.append("\u001b[0m")
    }


    @Volatile
    private var running = true

    override fun isRunning(): Boolean = running

    override fun requestExit() {
        running = false
    }
    // Shutdown the renderer and cleanup terminal state
    override fun shutdown() {
        disableMouseTracking()
        resetAttributes()
        showCursor()
        leaveAlternateScreen()
    }

    fun enterAlternateScreen() {
        output.append("\u001b[?1049h")
        output.append("\u001b[H")
    }

    fun leaveAlternateScreen() {
        output.append("\u001b[?1049l")
    }
}

/* =====================================================================
   StringSnapshotRenderer (for testing)

This renderer:

 - Performs no ANSI output
 - Maintains an internal 2D character buffer (grid)
 - Records all drawRect/drawText operations
 - Ignores colors/bold/italic/etc.
 - Does not generate events
 - Provides .snapshot() to retrieve textual output

Made for clean, deterministic tests.
   ===================================================================== */

class StringSnapshotRenderer(
    private val cols: Int = 120,
    private val rows: Int = 40
) : CanvasRenderer {

    private val buffer = Array(rows) { CharArray(cols) { ' ' } }

    override fun cols(): Int = cols
    override fun rows(): Int = rows

    override fun clear() {
        for (y in 0 until rows)
            for (x in 0 until cols)
                buffer[y][x] = ' '
    }

    override fun setColor(r: Int, g: Int, b: Int) {}
    override fun setBackgroundColor(r: Int, g: Int, b: Int) {}
    override fun bold(enabled: Boolean) {}
    override fun italic(enabled: Boolean) {}
    override fun underline(enabled: Boolean) {}
    override fun blink(enabled: Boolean) {}

    override fun drawRect(x: Int, y: Int, width: Int, height: Int) {
        for (yy in y until (y + height)) {
            if (yy !in 0 until this@StringSnapshotRenderer.rows) continue
            for (xx in x until (x + width)) {
                if (xx !in 0 until this@StringSnapshotRenderer.cols) continue
                buffer[yy][xx] = '#'
            }
        }
    }

    override fun drawText(x: Int, y: Int, text: String) {
        if (y !in 0 until rows) return
        var px = x
        for (c in text) {
            if (px in 0 until cols)
                buffer[y][px] = c
            px++
        }
    }

    override fun setCursorPosition(x: Int, y: Int) {}
    override fun flush() {}

    override fun pollEvent(): UIEvent? = null
    override fun tryPollEvent(): UIEvent? = null

    /* ============================================================
   Lifecycle / Terminal Control (no-op)
   ============================================================ */

    override fun enableMouseTracking() {}
    override fun disableMouseTracking() {}
    override fun hideCursor() {}
    override fun showCursor() {}
    override fun resetAttributes() {}
    @Volatile
    private var running = true

    override fun isRunning(): Boolean = running

    override fun requestExit() {
        running = false
    }
    override fun shutdown() {}

    fun snapshot(): String =
        buffer.joinToString("\n") { String(it) }
}

/* =====================================================================
   DOM MODEL WITH EXPLICIT EVENT CALLBACKS
   ===================================================================== */

data class DOMNode(
    val tag: String,
    val text: String? = null,
    val styleId: String? = null,
    val style: StyleSet = StyleSet(),
    val id: String? = null,
    var hasFocus: Boolean = false,

    // Event callbacks — all get UIEvent
    val onMouseDown: ((UIEvent) -> Unit)? = null,
    val onMouseUp: ((UIEvent) -> Unit)? = null,
    val onMouseMove: ((UIEvent) -> Unit)? = null,
    val onMouseScroll: ((UIEvent) -> Unit)? = null,
    val onKeyDown: ((UIEvent) -> Unit)? = null,
    val onKeyUp: ((UIEvent) -> Unit)? = null,
    val onFocusGained: ((UIEvent) -> Unit)? = null,
    val onFocusLost: ((UIEvent) -> Unit)? = null,
    val onResize: ((UIEvent) -> Unit)? = null,

    val children: List<DOMNode> = emptyList(),
    val key: String? = ""
)

fun Button(
    text: String,
    onClick: ((UIEvent) -> Unit)? = null,
    style: StyleSet = StyleSet(),
    key: String? = null
): DOMNode {
    val spaces = " ".repeat(text.length+2)
    return DOMNode(
        tag = "button",
        text = "$spaces\n $text \n$spaces",
        style = style,
        id = "button",
        onMouseDown = onClick,
    )
}
// Simple vertical splitter component: renders a vertical bar filling its styled height.
fun VerticalSplitter(
    style: StyleSet,
    onMouseDown: ((UIEvent) -> Unit)? = null,
    onMouseMove: ((UIEvent) -> Unit)? = null,
    onMouseUp: ((UIEvent) -> Unit)? = null,
    key: String? = null
): DOMNode {
    val top = style.top ?: 0
    val bottom = style.bottom ?: top
    val height = (bottom - top + 1).coerceAtLeast(1)
    val bar = buildString {
        repeat(height) { idx ->
            append('│')
            if (idx != height - 1) append('\n')
        }
    }
    return DOMNode(
        tag = "vertical-splitter",
        text = bar,
        style = style,
        id = "vertical-splitter",
        onMouseDown = onMouseDown,
        onMouseUp = onMouseUp,
        onMouseMove = onMouseMove,
    )
}

// Horizontal splitter component (single-row line).
fun HorizontalSplitter(
    style: StyleSet,
    onMouseDown: ((UIEvent) -> Unit)? = null,
    onMouseMove: ((UIEvent) -> Unit)? = null,
    onMouseUp: ((UIEvent) -> Unit)? = null,
    key: String? = null
): DOMNode {
    val left = style.left ?: 0
    val right = style.right ?: left
    val width = (right - left + 1).coerceAtLeast(1)
    val line = "─".repeat(width)
    return DOMNode(
        tag = "horizontal-splitter",
        text = line,
        style = style,
        id = "horizontal-splitter",
        onMouseDown = onMouseDown,
        onMouseUp = onMouseUp,
        onMouseMove = onMouseMove,
    )
}

private fun renderBuffer(buffer: ITextBuffer, width: Int, height: Int, startLine: Int = 0): String {
    val slice = buffer.viewportSlice(EditorViewport(0, startLine, width, height), gutterWidth = 0)
    return slice.lines.joinToString("\n") { line ->
        // Represent selection by inverted markup markers; rendering engine ignores them,
        // but we can use placeholders to hint selection (e.g., wrap in special chars).
        buildString {
            for (seg in line.segments) {
                if (seg.selected) {
                    append('\u001b').append("[7m") // inverse on
                    append(seg.text.ifEmpty { " " })
                    append('\u001b').append("[0m") // reset
                } else {
                    append(seg.text)
                }
            }
        }
    }
}

private fun handleKeyForBuffer(buffer: ITextBuffer, ev: UIEvent, singleLine: Boolean = false): Boolean {
    val key = ev.key ?: return false
    val ctrl = ev.ctrl
    val shift = ev.shift
    val before = buffer.text()

    when (key) {
        "Backspace" -> {
            buffer.deleteBackspace()
        }
        "Delete" -> {
            buffer.deleteForward()
        }
        "Enter" -> if (!singleLine) buffer.insertNewline()
        "Left" -> buffer.moveLeft(expand = shift, word = ctrl)
        "Right" -> buffer.moveRight(expand = shift, word = ctrl)
        "Up" -> buffer.moveUp(expand = shift)
        "Down" -> buffer.moveDown(expand = shift)
        "Home" -> buffer.moveStartOfLine(expand = shift)
        "End" -> buffer.moveEndOfLine(expand = shift)
        else -> {
            if (ctrl) {
                when (key.lowercase()) {
                    "c" -> buffer.copySelection()
                    "x" -> if (buffer.cutSelection()) {}
                    "v" -> buffer.pasteClipboard()
                    "a" -> buffer.selectAll()
                    "s" -> {} // placeholder for save hook
                }
            } else if (!ev.alt && key.length == 1) {
                buffer.insertText(key)
            }
        }
    }
    return buffer.text() != before
}

private fun handleMouseToBuffer(
    buffer: ITextBuffer,
    ev: UIEvent,
    singleLine: Boolean = false,
    scrollOffset: Int = 0,
    startSelection: Boolean = false,
    extendSelection: Boolean = false
) {
    val line = (ev.relY ?: 0).coerceAtLeast(0) + scrollOffset
    val col = ((ev.relX ?: 0) - 1).coerceAtLeast(0)
    val targetLine = if (singleLine) 0 else line
    val pos = Position(targetLine, col)
    if (startSelection) {
        buffer.startSelection(pos)
    } else if (extendSelection) {
        buffer.selectTo(pos)
    }
    buffer.moveCursorTo(pos, expand = extendSelection)
}

fun Textarea(
    tree: ComponentTreeManager,
    buffer: ITextBuffer,
    style: StyleSet,
    onChange: (ITextBuffer) -> Unit = {},
    onMouseDown: ((UIEvent) -> Unit)? = null,
    onMouseUp: ((UIEvent) -> Unit)? = null,
    onMouseMove: ((UIEvent) -> Unit)? = null,
    onKeyUp: ((UIEvent) -> Unit)? = null,
    key: String? = null
): DOMNode = renderComponent(tree, key) {
    val left = style.left ?: 0
    val right = style.right ?: left
    val totalWidth = (right - left + 1).coerceAtLeast(1)
    val top = style.top ?: 0
    val bottom = style.bottom ?: top
    val totalHeight = (bottom - top + 1).coerceAtLeast(7) // enforce min height

    val scrollbarWidth = 2
    val contentWidth = (totalWidth - scrollbarWidth).coerceAtLeast(1)
    val viewportHeight = totalHeight

    val (scrollOffset, setScrollOffset) = useState { 0 }
    val totalLines = buffer.text().split('\n').size
    val maxOffset = (totalLines - viewportHeight).coerceAtLeast(0)
    val clampedOffset = scrollOffset.coerceIn(0, maxOffset)

    val rendered = renderBuffer(buffer, contentWidth, viewportHeight, clampedOffset)

    val slice = buffer.viewportSlice(
        EditorViewport(0, clampedOffset, contentWidth, viewportHeight),
        gutterWidth = 0
    )
    val contentNode = DOMNode(
        tag = "textarea-content",
        text = rendered,
        style = StyleSet.parse("left:0; top:0; right:${contentWidth - 1}; bottom:${viewportHeight - 1}"),
        id = "textarea-content",
        onMouseDown = { ev ->
            handleMouseToBuffer(buffer, ev, singleLine = false, scrollOffset = clampedOffset, startSelection = true)
            onMouseDown?.invoke(ev)
        },
        onMouseUp = onMouseUp,
        onMouseMove = { ev ->
            if (ev.button != null) {
                handleMouseToBuffer(buffer, ev, singleLine = false, scrollOffset = clampedOffset, extendSelection = true)
                onMouseMove?.invoke(ev)
            }
        },
        onKeyDown = { ev ->
            if (handleKeyForBuffer(buffer, ev, singleLine = false)) onChange(buffer)
        },
        onKeyUp = onKeyUp,
    )
val scrollbar = VerticalScrollBar(
        tree = tree,
        style = StyleSet.parse("left:${contentWidth+1}; top:0; right:${contentWidth + scrollbarWidth}; bottom:${viewportHeight - 1}"),
        contentHeight = totalLines.coerceAtLeast(viewportHeight),
        scrollOffset = clampedOffset,
        onScrollTo = { off -> setScrollOffset(off.coerceIn(0, maxOffset)) }
    )

    val cursorFg = style.bg ?: Color(0, 0, 0)
    val cursorBg = style.fg ?: Color(200, 200, 200)
    val cursorNode = slice.cursor?.let { c ->
        val cx = c.column
        val cy = c.line
        val ch = c.char.firstOrNull()?.let { if (it.isWhitespace()) '_' else it } ?: '_'
        DOMNode(
            tag = "editor-cursor",
            text = "$ch",
            style = StyleSet(left = cx, top = cy, right = cx, bottom = cy, fg = cursorFg, bg = cursorBg),
            id = "editor-cursor"
        )
    }
    val alternateCursorNode = DOMNode(
            tag = "editor-cursor",
            text = "_",
            style = StyleSet(left = 0, top = 0, right = 0, bottom = 0, fg = cursorFg, bg = cursorBg),
            id = "editor-cursor"
        )

    // Apply viewport offset by adjusting buffer? simplest: re-render buffer with slice starting at offset
    val slicedRendered = renderBuffer(buffer, contentWidth, viewportHeight, clampedOffset)

    contentNode.copy(
        text = slicedRendered
    ).let { content ->
        DOMNode(
            tag = "textarea",
            text = null,
            style = style,
            id = "textarea",
            children = listOf(content, cursorNode?:alternateCursorNode, scrollbar)
        )
    }
}

fun InputText(
    buffer: ITextBuffer,
    style: StyleSet,
    onChange: (ITextBuffer) -> Unit = {},
    onMouseDown: ((UIEvent) -> Unit)? = null,
    onMouseUp: ((UIEvent) -> Unit)? = null,
    onMouseMove: ((UIEvent) -> Unit)? = null,
    onKeyUp: ((UIEvent) -> Unit)? = null,
    key: String? = null
): DOMNode {
    val left = style.left ?: 0
    val right = style.right ?: left
    val width = (right - left + 1).coerceAtLeast(1)
    val rendered = renderBuffer(buffer, width, 1)
    return DOMNode(
        tag = "input-text",
        text = rendered,
        style = style,
        id = "input-text",
        onMouseDown = { ev ->
            handleMouseToBuffer(buffer, ev, singleLine = true)
            onMouseDown?.invoke(ev)
        },
        onMouseUp = onMouseUp,
        onMouseMove = { ev ->
            handleMouseToBuffer(buffer, ev, singleLine = true)
            onMouseMove?.invoke(ev)
        },
        onKeyDown = { ev ->
            if (handleKeyForBuffer(buffer, ev, singleLine = true)) onChange(buffer)
        },
        onKeyUp = onKeyUp,
        key = key
    )
}

fun EditorView(
    tree: ComponentTreeManager,
    buffer: ITextBuffer,
    style: StyleSet,
    filePath: String,
    language: String,
    onChange: (ITextBuffer) -> Unit = {},
    onStateChange: (EditorState) -> Unit = {},
    key: String? = null
): DOMNode = renderComponent(tree, key) {
    val totalWidth = ((style.right ?: 0) - (style.left ?: 0) + 1).coerceAtLeast(10)
    val totalHeight = ((style.bottom ?: 0) - (style.top ?: 0) + 1).coerceAtLeast(10)
    val scrollbarWidth = 2
    val totalLines = buffer.text().split('\n').size.coerceAtLeast(1)
    val gutterWidth = (totalLines.toString().length + 1).coerceAtLeast(3)
    val contentWidth = (totalWidth - scrollbarWidth - gutterWidth).coerceAtLeast(1)
    val viewportHeight = totalHeight

    val (scrollOffset, setScrollOffset) = useState { 0 }
    val maxOffset = (totalLines - viewportHeight).coerceAtLeast(0)
    val clampedOffset = scrollOffset.coerceIn(0, maxOffset)

    val slice = buffer.viewportSlice(
        EditorViewport(0, clampedOffset, contentWidth, viewportHeight),
        gutterWidth = gutterWidth
    )
    val rendered = slice.lines.joinToString("\n") { line ->
        buildString {
            append(line.gutter)
            line.segments.forEach { append(it.text) }
        }
    }

    // Selection overlays (background only)
    val selectionNodes = mutableListOf<DOMNode>()
    slice.lines.forEachIndexed { idx, line ->
        var x = 0
        line.segments.forEach { seg ->
            val len = seg.text.length
            if (seg.selected && len > 0) {
                val left = gutterWidth + 1 + x  // align with caret offset
                val right = gutterWidth + 1 + x + len - 1
                selectionNodes.add(
                    DOMNode(
                        tag = "editor-selection",
                        text = seg.text,
                        style = StyleSet(
                            left = left,
                            top = idx,
                            right = right,
                            bottom = idx,
                            fg = style.fg?:Color(220, 220, 100),
                            bg = style.bg?:Color(34, 60, 97)
                        )
                    )
                )
            }
            x += len
        }
    }

    val contentNode = DOMNode(
        tag = "editor-content",
        text = rendered,
        style = StyleSet.parse("left:0; top:0; right:${gutterWidth + contentWidth - 1}; bottom:${viewportHeight - 1}"),
        id = "editor-content",
        onKeyDown = { ev ->
            if (handleKeyForBuffer(buffer, ev, singleLine = false)) onChange(buffer)
        },
        onMouseDown = { ev ->
            val adj = ev.alterCopy(UIEvent(kind = ev.kind, relX = (ev.relX ?: 0) - gutterWidth, relY = ev.relY))
            handleMouseToBuffer(buffer, adj, singleLine = false, scrollOffset = clampedOffset, startSelection = true)
        },
        onMouseMove = { ev ->
            if (ev.button != null) {
                val adj = ev.alterCopy(UIEvent(kind = ev.kind, relX = (ev.relX ?: 0) - gutterWidth, relY = ev.relY))
                handleMouseToBuffer(buffer, adj, singleLine = false, scrollOffset = clampedOffset, extendSelection = true)
            }
        }
    )

    val cursorNode = slice.cursor?.let { c ->
        val cx = gutterWidth + c.column + 1
        val cy = c.line
        val ch = c.char.firstOrNull()?.let { if (it.isWhitespace()) '_' else it } ?: '_'
        val fg = style.bg ?: Color(0, 0, 0)
        val bg = style.fg ?: Color(200, 200, 200)
        DOMNode(
            tag = "editor-cursor",
            text = "$ch",
            style = StyleSet(left = cx, top = cy, right = cx, bottom = cy, fg = fg, bg = bg),
            id = "editor-cursor"
        )
    }

    val scrollbar = VerticalScrollBar(
        tree = tree,
        style = StyleSet.parse("left:${gutterWidth + contentWidth}; top:0; right:${gutterWidth + contentWidth + scrollbarWidth - 1}; bottom:${viewportHeight - 1}"),
        contentHeight = totalLines.coerceAtLeast(viewportHeight),
        scrollOffset = clampedOffset,
        onScrollTo = { off -> setScrollOffset(off.coerceIn(0, maxOffset)) }
    )

    val children = listOfNotNull(contentNode, cursorNode) + selectionNodes + listOf(scrollbar)

    onStateChange(
        EditorState(
            filePath = filePath,
            language = language,
            cursorLine = buffer.cursorPosition().line,
            cursorColumn = buffer.cursorPosition().column,
            selection = buffer.selectionText(),
            totalLines = buffer.totalLines(),
            bom = buffer.bom(),
            encoding = buffer.encoding()
        )
    )

    DOMNode(
        tag = "editor",
        style = style,
        id = "editor",
        children = children
    )
}

fun VerticalScrollBar(
    tree: ComponentTreeManager,
    style: StyleSet,
    contentHeight: Int,
    scrollOffset: Int,
    onScrollTo: (Int) -> Unit,
    key: String? = null
): DOMNode = renderComponent(tree, key) {
    val vh = ((style.bottom ?: 0) - (style.top ?: 0) + 1).coerceAtLeast(5)
    val vw = ((style.right ?: 0) - (style.left ?: 0) + 1).coerceAtLeast(1)
    val ch = contentHeight.coerceAtLeast(vh)
    val maxOffset = (ch - vh).coerceAtLeast(0)
    val clampedOffset = scrollOffset.coerceIn(0, maxOffset)

    val indicatorHeight = ((vh * vh) / ch).coerceAtLeast(1)
    val trackRoom = (vh - indicatorHeight).coerceAtLeast(0)
    val indicatorTop = if (maxOffset == 0 || trackRoom == 0) 0 else (clampedOffset * trackRoom) / maxOffset

    val (dragging, setDragging) = useState { false }
    val (dragStartY, setDragStartY) = useState { 0 }
    val (dragStartTop, setDragStartTop) = useState { indicatorTop }

    fun toOffset(indicatorPos: Int): Int {
        val pos = indicatorPos.coerceIn(0, trackRoom)
        return if (trackRoom == 0 || maxOffset == 0) 0 else (pos * maxOffset) / trackRoom
    }

    // Wider visuals: two columns for track/indicator to make it easier to grab
    val indicatorStyle = StyleSet.parse(
        "left:0; top:${indicatorTop}; right:${vw}; bottom:${indicatorTop + indicatorHeight - 1}"
    )

    val indicator = DOMNode(
        id = "scrollbar-indicator",
        tag = "scrollbar-indicator",
        style = indicatorStyle,
        onMouseDown = { ev ->
            val y = ev.relY ?: 0
            setDragging(true)
            setDragStartY(y)
            setDragStartTop(indicatorTop)
        },
        onMouseUp = { _ -> setDragging(false) },
        onMouseMove = { ev ->
            if (!dragging) return@DOMNode
            val y = ev.relY ?: 0
            val dy = y - dragStartY
            val newTop = dragStartTop + dy
            onScrollTo(toOffset(newTop))
        },
    )

    val track = DOMNode(
        tag = "scrollbar-track",
        style = StyleSet.parse("left:0; top:0; right:${vw}; bottom:${vh - 1}"),
        id = "scrollbar-track",
        onMouseDown = { ev ->
            val y = ev.relY ?: 0
            val targetTop = (y - indicatorHeight / 2).coerceIn(0, trackRoom)
            onScrollTo(toOffset(targetTop))
        },
        children = listOf(indicator),
    )

    DOMNode(
        tag = "vertical-scrollbar",
        style = style,
        id = "vertical-scrollbar",
        children = listOf(track),
    )
}

// Simple vertical splitter component: renders a vertical bar filling its styled height.
fun VerticalSplitter(
    style: StyleSet,
    onMouseDown: ((UIEvent) -> Unit)? = null,
    onMouseMove: ((UIEvent) -> Unit)? = null,
    onMouseUp: ((UIEvent) -> Unit)? = null
): DOMNode {
    val top = style.top ?: 0
    val bottom = style.bottom ?: top
    val height = (bottom - top + 1).coerceAtLeast(1)
    val bar = buildString {
        repeat(height) { idx ->
            append('│')
            if (idx != height - 1) append('\n')
        }
    }
    return DOMNode(
        tag = "vertical-splitter",
        text = bar,
        style = style,
        id = "vertical-splitter",
        onMouseDown = onMouseDown,
        onMouseUp = onMouseUp,
        onMouseMove = onMouseMove,
    )
}

/* =====================================================================
   COMPONENT SYSTEM (hooks, instances)
   ===================================================================== */

data class ComponentInstance(
    val key: String?,
    val callSiteId: Int,
    val parent: ComponentInstance?,
) {
    val stateSlots = mutableListOf<Any?>()
    var nextHookIndex = 0
    fun beginRender() { nextHookIndex = 0 }
}

class ComponentTreeManager {

    private val instanceStack = ArrayDeque<ComponentInstance>()
    private val currentChildren = mutableMapOf<IdentityKey, ComponentInstance>()
    private val nextChildren = mutableMapOf<IdentityKey, ComponentInstance>()

    fun beginFrame() {
        nextChildren.clear()
        instanceStack.clear()
    }

    fun endFrame() {
        currentChildren.clear()
        currentChildren.putAll(nextChildren)
        nextChildren.clear()
    }

    fun enterComponent(callSiteId: Int, key: String?): ComponentInstance {
        val parent = instanceStack.lastOrNull()
        val position = nextChildren.keys.count {
            it.parent === parent && it.callSiteId == callSiteId && it.key == key
        }

        val identity = IdentityKey(parent, callSiteId, key, position)
        val instance = currentChildren.remove(identity)
            ?: ComponentInstance(key, callSiteId, parent)

        nextChildren[identity] = instance
        instance.beginRender()
        instanceStack.addLast(instance)
        return instance
    }

    fun exitComponent() { instanceStack.removeLast() }

    private data class IdentityKey(
        val parent: ComponentInstance?,
        val callSiteId: Int,
        val key: String?,
        val position: Int
    )
}

@Suppress("UNCHECKED_CAST")
class HookContext(private val instance: ComponentInstance) {
    fun <T> useState(initial: () -> T): Pair<T, (T) -> Unit> {
        val i = instance.nextHookIndex++
        if (i >= instance.stateSlots.size) {
            instance.stateSlots.add(initial())
        }
        val setter: (T) -> Unit = { v -> instance.stateSlots[i] = v }
        return instance.stateSlots[i] as T to setter
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <T> renderComponent(
    tree: ComponentTreeManager,
    key: String? = null,
    componentFn: HookContext.() -> T
): T {
    val callSiteId = Exception().stackTrace[1].lineNumber.hashCode()
    val instance = tree.enterComponent(callSiteId, key)
    val result = HookContext(instance).componentFn()
    tree.exitComponent()
    return result
}

/* =====================================================================
   EVENT DISPATCH + HIT TESTING + FOCUS LOGIC
   ===================================================================== */

private fun hitTest(x: Int, y: Int, node: DOMNode, parentX: Int, parentY: Int): Boolean {
    val left   = node.style.left   ?: 0
    val top    = node.style.top    ?: 0
    val right  = node.style.right  ?: 0
    val bottom = node.style.bottom ?: 0

    val x1 = parentX + left
    val y1 = parentY + top
    // right/bottom are inclusive cell indexes; +1 to make them exclusive in the check
    val x2 = parentX + right + 1
    val y2 = parentY + bottom + 1

    return x in x1 until x2 && y in y1 until y2
}

private fun findTopmostHit(node: DOMNode, event: UIEvent, parentX: Int, parentY: Int): DOMNode? {
    val x = event.x ?: return null
    val y = event.y ?: return null

    val left   = node.style.left   ?: 0
    val top    = node.style.top    ?: 0
    val right  = node.style.right  ?: 0
    val bottom = node.style.bottom ?: 0

    val x1 = parentX + left
    val y1 = parentY + top
    val x2 = parentX + right + 1
    val y2 = parentY + bottom + 1

    var hitChild: DOMNode? = null
    for (child in node.children) {
        val childHit = findTopmostHit(child, event, x1, y1)
        if (childHit != null) hitChild = childHit
    }
    if (hitChild != null) return hitChild
    return if (x in x1 until x2 && y in y1 until y2) node else null
}

private fun clearFocus(node: DOMNode) {
    node.hasFocus = false
    node.children.forEach { clearFocus(it) }
}

private fun setFocus(node: DOMNode, id: String): Boolean {
    if (node.id == id) {
        node.hasFocus = true
        return true
    }
    for (child in node.children) {
        if (setFocus(child, id)) return true
    }
    return false
}

private fun findNodeById(node: DOMNode, id: String): DOMNode? {
    if (node.id == id) return node
    for (child in node.children) {
        val found = findNodeById(child, id)
        if (found != null) return found
    }
    return null
}

private fun dispatchEventToDom(node: DOMNode, event: UIEvent, parentX: Int, parentY: Int) {

    // Traverse children first (deepest-first)
    for (child in node.children) {
        dispatchEventToDom(child, event, parentX + (node.style.left ?: 0), parentY + (node.style.top ?: 0))
    }

    val x = event.x
    val y = event.y

    val inside = if (x != null && y != null) hitTest(x, y, node, parentX, parentY) else false
    val localizedEvent = event.alterCopy(UIEvent(kind=event.kind,relX= x?.minus(parentX),relY= y?.minus(parentY)))
    when (event.kind) {
        "mouse_down"   -> if (inside) node.onMouseDown?.invoke(localizedEvent)
        "mouse_up"     -> if (inside) node.onMouseUp?.invoke(localizedEvent)
        "mouse_move",
        "mouse_drag"   -> if (inside) node.onMouseMove?.invoke(localizedEvent)
        "mouse_scroll" -> if (inside) node.onMouseScroll?.invoke(localizedEvent)
        "key_down"     -> if (node.hasFocus) node.onKeyDown?.invoke(localizedEvent)
        "key_up"       -> if (node.hasFocus) node.onKeyUp?.invoke(localizedEvent)
        "focus_gained" -> if (node.hasFocus) node.onFocusGained?.invoke(localizedEvent)
        "focus_lost"   -> if (!node.hasFocus) node.onFocusLost?.invoke(localizedEvent)
        "resize"       -> node.onResize?.invoke(localizedEvent)
    }
}

fun dispatchEvent(root: DOMNode, event: UIEvent) {
    dispatchEventToDom(root, event, 0, 0)
}

/* =====================================================================
   RENDERING ENGINE
   ===================================================================== */

fun renderDomTree(renderer: CanvasRenderer, dom: DOMNode, parentX: Int = 0, parentY: Int = 0) {

    val left   = dom.style.left   ?: 0
    val top    = dom.style.top    ?: 0
    val right  = dom.style.right  ?: 0
    val bottom = dom.style.bottom ?: 0

    val x1 = parentX + left
    val y1 = parentY + top
    val x2 = parentX + right
    val y2 = parentY + bottom

    val width  = (x2 - x1 + 1).coerceAtLeast(1)
    val height = (y2 - y1 + 1).coerceAtLeast(1)

    dom.style.bg?.let { c ->
        renderer.setBackgroundColor(c.r, c.g, c.b)
        renderer.drawRect(x1, y1, width, height)
    }

    dom.style.fg?.let { c ->
        renderer.setColor(c.r, c.g, c.b)
    }

    val deco = dom.style.textDecoration
    renderer.bold(deco?.contains("bold") == true)
    renderer.italic(deco?.contains("italic") == true)
    renderer.underline(deco?.contains("underline") == true)
    renderer.blink(deco?.contains("blink") == true)

    dom.text?.let {
        // Render multiline text manually (CanvasRenderer has no wrapping)
        val lines = it.split('\n')
        var yy = y1
        for (line in lines) {
            renderer.drawText(x1, yy, line)
            yy++
        }
    }

    for (child in dom.children)
        renderDomTree(renderer, child, x1, y1)
}

/* =====================================================================
   SAMPLE COMPONENTS
   ===================================================================== */


fun counterComponent(tree: ComponentTreeManager, key: String? = null): DOMNode =
    renderComponent(tree, key) {
        val (count, setCount) = useState { 0 }
        Button(
            text = "Count: $count",
            onClick = { _ -> setCount(count + 1) }
        )
    }

fun listOfCounters(tree: ComponentTreeManager, values: List<Int>): DOMNode =
    DOMNode(
        tag = "list-counter-container",
        id = "list-counter-container",
        children = values.map { v -> counterComponent(tree, key = v.toString()) },
    )

/* =====================================================================
   APP COMPONENTS
   ===================================================================== */


fun VerticalTabsHost(
    tree: ComponentTreeManager,
    style: StyleSet,
    tabs: LinkedHashMap<String, (ComponentTreeManager) -> DOMNode>,
    onTabChanged: (String) -> Unit,
    key: String? = null
): DOMNode = renderComponent(tree, key) {
    val hostWidth = (style.right ?: 0) - (style.left ?: 0) + 1 - 1
    val hostHeight = (style.bottom ?: 0) - (style.top ?: 0) + 1
    val stripeWidth = 6
    val buttonHeight = 3
    val initialTab = tabs.keys.firstOrNull()
    val (activeTab, setActiveTab) = useState { initialTab ?: "" }

    fun makeLabel(name: String): String {
        val short = name.take(stripeWidth).padEnd(stripeWidth, ' ')
        return buildString {
            append(" ".repeat(stripeWidth)).append('\n')
            append(short).append('\n')
            append(" ".repeat(stripeWidth))
        }
    }

    val buttons = tabs.entries.mapIndexed { idx, entry ->
        val top = idx * buttonHeight
        val bottom = top + buttonHeight - 1
        val selected = entry.key == activeTab
        val tabTag = if (selected) "tab-button::selection" else "tab-button"

        DOMNode(
            tag = tabTag,
            id = tabTag,
            key=key,
            text = makeLabel(entry.key),
            style = StyleSet.parse("left:0; top:${top}; right:${stripeWidth - 1}; bottom:${bottom}"),
            onMouseDown = {
                if (activeTab != entry.key) {
                    setActiveTab(entry.key)
                    onTabChanged(entry.key)
                }
            }
        )
    }

    val contentWidth = (hostWidth - stripeWidth).coerceAtLeast(1)
    val content = tabs[activeTab]?.invoke(tree)

    DOMNode(
        tag = "vertical-tabs-host",
        id = "vertical-tabs-host",
        style = style,
        children = listOfNotNull(
                DOMNode(
                    tag = "tab-strip",
                    style = StyleSet.parse("left:0; top:0; right:${stripeWidth - 1}; bottom:${(hostHeight - 1).coerceAtLeast(0)}"),
                    id = "tab-strip",
                    children = buttons,
                ),
            content?.let {
                DOMNode(
                    tag = "tab-content-area",
                    style = StyleSet.parse("left:${stripeWidth}; top:0; right:${stripeWidth + contentWidth - 1}; bottom:${(hostHeight - 1).coerceAtLeast(0)}"),
                    id = "tab-content-area",
                    children = listOf(it),
                )
            }
        ),
    )
}

fun FileTreeComponent(
    tag:String ="",
    id:String ="",
    tree: ComponentTreeManager,
    rootDir: String,
    style: StyleSet,
    key: String? = null,
    selected: FileTreeEntry?=null,
    onFileSelected: (FileTreeEntry) -> Unit = {},
    onFolderSelected: (FileTreeEntry) -> Unit = {}
): DOMNode =  renderComponent(tree, key)  {
    val safeWidth = ( (style.right ?: 0) - (style.left ?: 0) + 1 ).coerceAtLeast(20)
    val safeHeight = ( (style.bottom ?: 0) - (style.top ?: 0) + 1 ).coerceAtLeast(10)
    val (fileTree, _) = useState { FileTree.newFileTree(rootDir) }
    val (version, setVersion) = useState { 0 } // version to trigger re-render
    val (scrollOffset, setScrollOffset) = useState { 0 }

    val viewportWidth = (safeWidth - 1).coerceAtLeast(1) // leave 1 column for scrollbar
    val viewportHeight = safeHeight

    val entries = fileTree.flattened()
    val maxOffset = (entries.size - viewportHeight).coerceAtLeast(0)
    val clampedScroll = scrollOffset.coerceIn(0, maxOffset)
    val lines = entries
        .drop(clampedScroll)
        .take(viewportHeight)
        .mapIndexed { idx, entry ->
        val indent = "  ".repeat(entry.padding)
        val prefix = when (entry.typ) {
            "folder" -> if (entry.isOpen) "[-] " else "[+] "
            else -> "    "
        }
        val label = (indent + prefix + entry.name).take(viewportWidth-2)
        val text = label.padEnd(viewportWidth-2, ' ')
        val bg = if (entry.typ == "folder") "#4c548f;text-decoration:bold" else "#3c4678"
        val fg = if (entry.fullPath == selected?.fullPath) "#fd8d1d;text-decoration:bold" else "#e0e0e6"

            DOMNode(
                tag = "${tag}:entry",
                id = "${tag}:entry",
                key = entry.fullPath,
                text = text,
                style = StyleSet.parse("left:0;top:${idx};right:${viewportWidth - 3};bottom:${idx};bg:$bg;fg:$fg"),
                onMouseDown = { event: UIEvent ->
                    if (entry.typ == "folder") {
                        val openerColumn = run {
                            val plusIdx = text.indexOf("[+]")
                            val minusIdx = text.indexOf("[-]")
                            when {
                                plusIdx >= 0 -> plusIdx
                                minusIdx >= 0 -> minusIdx
                                else -> -1
                            }
                        }
                        val toggleHit = openerColumn >= 0 &&
                            (event.relX ?: -1) in openerColumn..(openerColumn + 2)
                        if (toggleHit) {
                            fileTree.toggle(entry.fullPath)
                            fileTree.refreshOpenNodes()
                            setVersion(version + 1)
                        } else {
                            onFolderSelected(entry)
                        }
                    } else {
                        onFileSelected(entry)
                    }
                }
            )
        }

    val scrollbar = VerticalScrollBar(
        tree = tree,
        style = StyleSet.parse("left:${safeWidth - 2}; top:0; right:${safeWidth - 1}; bottom:${safeHeight - 1}"),
        contentHeight = entries.size.coerceAtLeast(viewportHeight),
        scrollOffset = clampedScroll,
        onScrollTo = { off -> setScrollOffset(off.coerceIn(0, maxOffset)) }
    )

    DOMNode(
        tag = tag,
        style = StyleSet.parse("left:0;top:0;right:${safeWidth - 1};bottom:${safeHeight - 1}"),
        id = tag,
        children = lines + scrollbar,
    )
}

/* =====================================================================
   GIT PANEL COMPONENT (read-only actions + layout scaffolding)
   ===================================================================== */

private val commitDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault())

fun GitComponent(
    tree: ComponentTreeManager,
    workspaceRoot: String,
    style: StyleSet,
    key: String? = null,
    gitFactory: (File) -> GitService = { root -> JGitService(root) },
    onCommit: (String) -> Unit = {},
    onTag: (String) -> Unit = {},
    onPush: () -> Unit = {}
): DOMNode = renderComponent(tree, key) {
    val safeWidth = ((style.right ?: 0) - (style.left ?: 0) + 1).coerceAtLeast(20)
    val safeHeight = ((style.bottom ?: 0) - (style.top ?: 0) + 1).coerceAtLeast(12)
    val (gitService, _) = useState { gitFactory(File(workspaceRoot)) }
    val statusEntries = gitService.statusPorcelain()
    val commits = gitService.listCommits(40)
    val branch = gitService.currentBranch()
    val (message, setMessage) = useState { "" }
    val (commitScroll, setCommitScroll) = useState { 0 }
    val statusText = if (statusEntries.isEmpty()) "(clean)"
        else statusEntries.joinToString("\n") { "${it.code.padEnd(2)} ${(it.path + " ".repeat(safeWidth)).substring(0,safeWidth-3)}" }

    val commitLineWidth = (safeWidth - 2).coerceAtLeast(20)
    val commitLines = commits.map { c ->
        val hashShort = c.hash.take(8).padEnd(8, ' ')
        val dateStr = c.date?.let { commitDateFormatter.format(it) } ?: "----"
        val author = c.author.take(12).padEnd(12, ' ')
        val msg = c.message.lines().firstOrNull() ?: ""
        "$hashShort  $dateStr  $author  $msg"
    }.map { line -> line.take(commitLineWidth) }

    val header = DOMNode(
        tag = "git-header",
        text = "origin/$branch",
        style = StyleSet.parse("left:0; top:0; right:${safeWidth}; bottom:0"),
        id = "git-header",
    )

    val statusBoxHeight = (safeHeight / 3).coerceAtLeast(5)
    val statusBox = DOMNode(
        tag = "git-status",
        text = statusText,
        style = StyleSet.parse("left:0; top:1; right:${safeWidth}; bottom:${statusBoxHeight}"),
        id = "git-status",
    )

    val splitter1 = HorizontalSplitter(
        style = StyleSet.parse("left:0; top:${statusBoxHeight + 1}; right:${safeWidth}; bottom:${statusBoxHeight + 1}")
    )

    val messageBoxTop = statusBoxHeight + 2
    val messageBoxHeight = 10
    // val messageLabel = DOMNode(
    //     tag = "git-message-label",
    //     text = "${workspaceRoot}\nMessage",
    //     style = StyleSet.parse("left:0; top:${messageBoxTop}; right:${safeWidth - 1}; bottom:${messageBoxTop}"),
    //     id = "git-message-label",
    // )

    val (messageBuf, _) = useState { TextBuffer().apply { loadText(message) } }
    val messageArea = Textarea(
        tree = tree,
        buffer = messageBuf,
        style = StyleSet.parse("left:0; top:${messageBoxTop}; right:${safeWidth -2}; bottom:${messageBoxTop + messageBoxHeight}"),
        onChange = { buf -> setMessage(buf.text()) }
    )

    val buttonsTop = messageBoxTop + messageBoxHeight + 1
    val buttonWidth = (safeWidth / 3).coerceAtLeast(8)
    val commitBtn = Button(
        text = "commit",
        style = StyleSet.parse("left:1; top:${buttonsTop}; right:${6}; bottom:${buttonsTop}"),
        onClick ={ onCommit(message) }
    )
    val tagBtn = Button(
        text = "--tag--",
        style = StyleSet.parse("left:${safeWidth/2 - 4}; top:${buttonsTop}; right:${14}; bottom:${buttonsTop}"),
        onClick = { onTag(message) }
    )
    val pushBtn = Button(
        text = "-push-",
        style = StyleSet.parse("left:${safeWidth-11}; top:${buttonsTop}; right:${19}; bottom:${buttonsTop}"),
        onClick = { onPush() }
    )

    val commitsTop = buttonsTop + 3
    val commitViewportHeight = (safeHeight - commitsTop).coerceAtLeast(3)
    val maxCommitOffset = (commitLines.size - commitViewportHeight).coerceAtLeast(0)
    val clampedCommitScroll = commitScroll.coerceIn(0, maxCommitOffset)
    val commitText = commitLines.drop(clampedCommitScroll).take(commitViewportHeight).joinToString("\n")
    val commitTextBox = DOMNode(
        tag = "git-commits",
        text = commitText,
        style = StyleSet.parse("left:0; top:${commitsTop}; right:${safeWidth - 3}; bottom:${safeHeight - 1}"),
        id = "git-commits",
    )
    val commitScrollbar = VerticalScrollBar(
        tree = tree,
        style = StyleSet.parse("left:${safeWidth - 2}; top:${commitsTop}; right:${safeWidth - 1}; bottom:${safeHeight - 1}"),
        contentHeight = commitLines.size.coerceAtLeast(commitViewportHeight),
        scrollOffset = clampedCommitScroll,
        onScrollTo = { newOffset -> setCommitScroll(newOffset.coerceIn(0, maxCommitOffset)) }
    )

    DOMNode(
        tag = "git-panel",
        style = style,
        id = "git-panel",
        children = listOf(
            header,
            statusBox,
            splitter1,
            // messageLabel,
            messageArea,
            commitBtn,
            tagBtn,
            pushBtn,
            commitTextBox,
            commitScrollbar
        ),
    )
}
/* =====================================================================
   APP DEMO (header + sidebar + splitter + content + status)
   Uses HookContext.useState for per-instance state.
   ===================================================================== */

fun App(tree: ComponentTreeManager, cols: Int, rows: Int): DOMNode =
    renderComponent(tree) {
        // --- Global-ish app state stored in hook slots
    val (splitterPos, setSplitterPos) = useState { 40 }
    val (dragging, setDragging) = useState { false }
    val (dragStartX, setDragStartX) = useState { 0 }
    val (dragStartSplit, setDragStartSplit) = useState { splitterPos }
    val (status, setStatus) = useState { "Ready" }
    val (selectedFileTreeEntry,setSelectedFileTreeEntry) = useState<FileTreeEntry?> { null }
    val (editorBuffer, _) = useState { TextBuffer() }
    val (loadedPath, setLoadedPath) = useState { "" }
        val (mouseAbs, setMouseAbs) = useState { Pair(0, 0) }
        val (mouseRel, setMouseRel) = useState { Pair(0, 0) }
    val statText = "Pos:$splitterPos,drag:$dragging,StartX:$dragStartX,Split:$dragStartSplit"

        // --- Layout math
        val minPanelWidth = 20
        val maxPanelWidth = (cols - 4).coerceAtLeast(minPanelWidth)
        val clampedSplit = splitterPos.coerceIn(minPanelWidth, maxPanelWidth)
        val mainHeight = rows - 2
        val tabContentWidth = (clampedSplit - 5).coerceAtLeast(1)
        val workspaceRoot = System.getProperty("user.dir") ?: "."
        fun currentBranch(): String =
            runCatching { runCommand("sh", "-c", "git rev-parse --abbrev-ref HEAD")?.trim().orEmpty() }.getOrDefault("")
        if (selectedFileTreeEntry?.typ == "file" && selectedFileTreeEntry.fullPath != loadedPath) {
            val content = runCatching { File(selectedFileTreeEntry.fullPath).readText() }
                .getOrElse { err -> "Unable to read file:\\n${err.message ?: err.toString()}" }
            editorBuffer.loadText(content)
            setLoadedPath(selectedFileTreeEntry.fullPath)
        }

        // --- Components
        val header = DOMNode(
            tag = "header",
            id = "header",
            text = " Kotlin TUI Demo (q=quit) ",
            style = StyleSet.parse("left:0; top:0; right:${cols - 1}; bottom:0"),
            onMouseMove = {ev ->
                // setStatus("$statText,header,hover,x${ev.x},y${ev.y}")
            }
        )
        val tt = 55
        val sidebar = DOMNode(
            tag = "sidebar",
            id = "sidebar",
            style = StyleSet.parse("left:0; top:0; right:${clampedSplit - 1}; bottom:${mainHeight - 1}"),
            children = listOf(
                VerticalTabsHost(
                    tree = tree,
                    style = StyleSet.parse("left:0; top:0; right:${clampedSplit - 1}; bottom:${mainHeight - 1}"),
                    tabs = linkedMapOf("Files" to { _ ->
                            FileTreeComponent(
                                tag = "files-tab",
                                id = "files-tab",
                                tree = tree,
                                rootDir = workspaceRoot,
                                selected=selectedFileTreeEntry,
                                style = StyleSet.parse("left:0; top:0; right:${tabContentWidth - 4}; bottom:${mainHeight - 1}"),
                                onFileSelected = { entry ->
                                    // setStatus("File Selected ${entry.fullPath}")
                                    setSelectedFileTreeEntry(entry)
                                },
                                onFolderSelected = { entry ->
                                    // setStatus("Folder Selected ${entry.fullPath}")
                                    setSelectedFileTreeEntry(entry)
                                }
                            )
                        },
                        "Git" to { _ ->
                            GitComponent(
                                tree = tree,
                                workspaceRoot = workspaceRoot,
                                style = StyleSet.parse("left:0; top:0; right:${tabContentWidth - 4}; bottom:${mainHeight - 1}")
                            )
                        },
                        "Logs" to { _ ->
                            DOMNode(
                                tag = "logs-tab",
                                text = "Logs\n[recent events]",
                                style = StyleSet.parse("left:0; top:0; right:${tabContentWidth - 4}; bottom:${mainHeight - 1}"),
                                id = "logs-tab",
                            )
                        }
                    ),
                    onTabChanged = { name -> /*setStatus("Tab -> $name")*/ }
                )
            )
        )

        // Splitter with simple drag logic in-place
        val splitter = DOMNode(
            tag = "splitter",
            id = "splitter",
            text = "⣿\n".repeat(mainHeight),
            style = StyleSet.parse("left:${clampedSplit - 1}; top:0; right:${clampedSplit - 1}; bottom:${mainHeight - 1}"),
            onMouseDown = { ev ->
                val mx = ev.x ?: return@DOMNode
                setDragging(true)
                setDragStartX(mx)
                setDragStartSplit(clampedSplit)
                // setStatus("Splitter grab @${ev.x},${ev.y}")
            },
        )

        val content = EditorView(
            tree = tree,
            buffer = editorBuffer,
            style = StyleSet.parse("left:${clampedSplit+3}; top:0; right:${cols - 2}; bottom:${mainHeight - 1}"),
            filePath = loadedPath,
            language = when (loadedPath.substringAfterLast('.', "")) {
                "kt" -> "Kotlin"
                "java" -> "Java"
                "md" -> "Markdown"
                "py" -> "Python"
                else -> "Text"
            },
            onChange = { _ -> /*setStatus("Edited ${loadedPath}")*/ },
            onStateChange = { state ->
                val time = java.time.LocalTime.now().withNano(0)
                val statusLine = listOf(
                    "$time",
                    currentBranch(),
                    workspaceRoot,
                    "${mouseAbs.first},${mouseAbs.second} rel ${mouseRel.first},${mouseRel.second}",
                    "${state.filePath.replace(workspaceRoot,"")} ${state.language} line ${state.cursorLine + 1}:${state.cursorColumn + 1} sel=${state.selection.length}"
                )
                setStatus(statusLine.joinToString(" | "))
            }
        )

        val statusBar = DOMNode(
            tag = "footer",
            id = "footer",
            text = "$status | split=$clampedSplit drag=$dragging",
            style = StyleSet.parse("left:0; top:${rows - 1}; right:${cols - 1}; bottom:${rows - 1}"),
            onMouseMove = {ev ->
                // setStatus("$statText,footer,hover,x${ev.x},y${ev.y}")
            }
        )

        val mainArea = DOMNode(
            tag = "main-area",
            id = "main-area",
            style = StyleSet.parse("left:0; top:1; right:${cols - 1}; bottom:${rows - 2}"),
            children = listOf(sidebar, splitter, content),
            onMouseMove = {ev ->

                val mx = ev.x ?: return@DOMNode
                ev.y?.let { setMouseAbs(mx to it) }
                ev.relX?.let { rx -> ev.relY?.let { ry -> setMouseRel(rx to ry) } }
                if (dragging) {
                    val dx = mx - dragStartX
                    setSplitterPos((dragStartSplit + dx).coerceIn(minPanelWidth, maxPanelWidth))
                    // setStatus("Splitter drag ${mx},${ev.y}")
                } else {
                    // setStatus("$statText,main-area,x${ev.x},y${ev.y}")
                }
            },
            onMouseUp = { ev ->
                setDragging(false)
            }
        )

        // Root composes everything
        DOMNode(
            tag = "root",
            style = StyleSet.parse("left:0; top:0; right:${cols - 1}; bottom:${rows - 1}"),
            id = "root",
            children = listOf(header, mainArea, statusBar),
        )
    }

/* =====================================================================
   WIRED APPLICATION RUNNER
   ===================================================================== */
data class AppContext(
    var onFrame: (number: ULong) -> Unit = {},
    var onError: (x:Throwable) -> Unit = {},
    var onExit: () -> Unit = {},
)

// ============ Terminal raw mode helpers ============
private fun runCommand(vararg cmd: String): String? = try {
    ProcessBuilder(*cmd)
        .redirectErrorStream(true)
        .start()
        .inputStream.bufferedReader().use { it.readText() }
} catch (_: Exception) { null }

private fun enterRawMode(): String? {
    val state = runCommand("sh", "-c", "stty -g < /dev/tty")?.trim()
    runCommand("sh", "-c", "stty raw -echo < /dev/tty")
    return state
}

private fun restoreStty(state: String?) {
    val cmd = if (state != null) {
        "stty $state < /dev/tty"
    } else {
        // If we failed to capture the previous state, at least return to a sane, echoed mode.
        "stty sane -echo echo icanon isig < /dev/tty"
    }
    runCommand("sh", "-c", cmd)
}

fun runApp(
    renderer: CanvasRenderer,
    maxFrames: ULong? = null,
    styleFiles: List<String> = emptyList(),
    rootFn: (ComponentTreeManager) -> DOMNode
) : AppContext {
    val appContext: AppContext = AppContext()
    val tree = ComponentTreeManager()
    var lastDom: DOMNode = DOMNode("empty",)
    var focusedId: String? = null
    // var focusedId: String? = null
    val styleSheet = StyleSheet.loadFromFiles(styleFiles)

    // Try to enter raw mode for ANSI terminals so key/mouse events work and echo is off.
    val savedStty = if (renderer is AnsiCanvasRenderer) enterRawMode() else null

    // Best-effort terminal prep if supported
    (renderer as? AnsiCanvasRenderer)?.enterAlternateScreen()
    renderer.enableMouseTracking()
    renderer.hideCursor()

    var frame: Long = 0

    try {
        while (renderer.isRunning()) {
            frame += 1

            // Render frame
            tree.beginFrame()
            val rawRoot = rootFn(tree)
            clearFocus(rawRoot)
            focusedId?.let { setFocus(rawRoot, it) }
            val root = applyStyles(rawRoot, styleSheet)
            tree.endFrame()
            lastDom = root

            renderer.clear()
            renderDomTree(renderer, root)
            renderer.flush()

            // Poll a single event (non-blocking) after rendering
            val event = renderer.tryPollEvent()
            if (event != null) {
                if ((event.key == "Esc") || (event.ctrl && event.key == "q")|| (event.key == "~")) {
                    renderer.requestExit()
                } else {
                    dispatchEventToDom(lastDom, event, 0, 0)
                    if (event.x != null && event.y != null) {
                        val hit = findTopmostHit(lastDom, event, 0, 0)
                        if (hit?.id != null) {
                            focusedId = hit.id
                        }
                    }
                }
            } else {
                // avoid busy loop when renderer provides no events
                Thread.sleep(10)
            }

            appContext.onFrame(frame.toULong())

            // Optional frame cap
            if (maxFrames != null && frame.toULong() >= maxFrames) {
                renderer.requestExit()
            }
        }
        appContext.onExit()
    } catch (x: Throwable) {
        println(x)
        appContext.onError(x)
    } finally {
        // CLEANUP GUARANTEED
        renderer.resetAttributes()
        renderer.disableMouseTracking()
        renderer.showCursor()
        renderer.shutdown()
        if (renderer is AnsiCanvasRenderer) {
            restoreStty(savedStty)
            // Safety: ensure terminal is restored even if stty state was missing or broken.
            runCommand("sh", "-c", "stty sane echo icanon isig < /dev/tty")
            renderer.leaveAlternateScreen()
        }
        appContext.onExit()
    }
    return appContext
}
/* =====================================================================
   APPLICATION ENTRY POINT
   ===================================================================== */

fun main() {
    // Swap renderer implementation here:
    //  - StringSnapshotRenderer: single-frame render, prints buffer, exits
    //  - NoopRenderer: single-frame render, no output
    //  - AnsiCanvasRenderer: interactive loop (ensure your terminal is in raw mode)

    val renderer: CanvasRenderer = AnsiCanvasRenderer(initialCols = 120, initialRows = 40)
    val maxFrames = if (renderer is StringSnapshotRenderer || renderer is NoopRenderer) 1 else null

    runApp(
        renderer = renderer,
        maxFrames = maxFrames?.toULong(),
        styleFiles = listOf("styles/app.css"),
    ) { tree: ComponentTreeManager ->
        App(tree, renderer.cols(), renderer.rows())
    }.apply {
        onExit = {
            when (renderer) {
                is StringSnapshotRenderer -> println(renderer.snapshot())
            }
        }
        onError = { println(it) }
    }
}


/* =====================================================================
   END OF FILE
   ===================================================================== */
