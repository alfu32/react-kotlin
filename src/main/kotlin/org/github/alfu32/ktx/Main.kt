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
    val focusId: String? = null,
    val cols: Int? = null,      // resize cols
    val rows: Int? = null       // resize rows
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
    if (sheet != null && dom.id != null) {
        resolvedStyle.mergeFrom(sheet.getStyle(dom.id))
    }
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
            return UIEvent(kind="key_down", key="Esc")
        }

        // Simple printable chars
        val ch = firstByte.toChar()
        return UIEvent(kind="key_down", key="$ch")
    }

    private fun parseCsi(): UIEvent? {
        val seq = StringBuilder()
        while (input.available() > 0) {
            val c = input.read().toChar()
            seq.append(c)
            if ((c in 'A'..'Z') || (c in 'a'..'z')) break
        }
        val s = seq.toString()

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

                // Scroll wheel
                if (scroll == 64) return UIEvent("mouse_scroll", x = x, y = y, scrollDelta = 1)
                if (scroll == 65) return UIEvent("mouse_scroll", x = x, y = y, scrollDelta = -1)

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
                return UIEvent(kind, x = x, y = y, button = button)
            }
        }

        // Arrow keys
        return when (s) {
            "A" -> UIEvent("key_down", key="Up")
            "B" -> UIEvent("key_down", key="Down")
            "C" -> UIEvent("key_down", key="Right")
            "D" -> UIEvent("key_down", key="Left")
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

fun Textarea(
    text: String,
    style: StyleSet,
    onChange: (old: String, new: String) -> Unit = { _, _ -> },
    onMouseDown: ((UIEvent) -> Unit)? = null,
    onMouseUp: ((UIEvent) -> Unit)? = null,
    onMouseMove: ((UIEvent) -> Unit)? = null,
    onKeyUp: ((UIEvent) -> Unit)? = null,
    key: String? = null
): DOMNode =
    DOMNode(
        tag = "textarea",
        text = text,
        style = style,
        id = "textarea",
        onMouseDown = onMouseDown,
        onMouseUp = onMouseUp,
        onMouseMove = onMouseMove,
        onKeyDown = { ev ->
            val k = ev.key ?: return@DOMNode
            val next = when (k) {
                "Backspace" -> if (text.isNotEmpty()) text.dropLast(1) else text
                "Enter" -> text + "\n"
                else -> if (k.length == 1) text + k else text
            }
            if (next != text) onChange(text, next)
        },
        onKeyUp = onKeyUp,
    )

fun VerticalScrollBar(
    tree: ComponentTreeManager,
    style: StyleSet,
    contentHeight: Int,
    scrollOffset: Int,
    onScrollTo: (Int) -> Unit,
    key: String? = null
): DOMNode = renderComponent(tree, key) {
    val vh = ((style.bottom ?: 0) - (style.top ?: 0) + 1).coerceAtLeast(1)
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
        "left:0; top:${indicatorTop}; right:1; bottom:${indicatorTop + indicatorHeight - 1}"
    )

    val indicator = DOMNode(
        tag = "scrollbar-indicator",
        style = indicatorStyle,
        id = "scrollbar-indicator",
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
        style = StyleSet.parse("left:0; top:0; right:1; bottom:${vh - 1}"),
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
        "key_down"     -> node.onKeyDown?.invoke(localizedEvent)
        "key_up"       -> node.onKeyUp?.invoke(localizedEvent)
        "focus_gained" -> node.onFocusGained?.invoke(localizedEvent)
        "focus_lost"   -> node.onFocusLost?.invoke(localizedEvent)
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
    val hostWidth = (style.right ?: 0) - (style.left ?: 0) + 1
    val hostHeight = (style.bottom ?: 0) - (style.top ?: 0) + 1
    val stripeWidth = 10
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
            style = StyleSet.parse("left:0;top:${idx};right:${viewportWidth - 2};bottom:${idx};bg:$bg;fg:$fg"),
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
        style = StyleSet.parse("left:${safeWidth - 3}; top:0; right:${safeWidth - 2}; bottom:${safeHeight - 1}"),
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
    val statusText = if (statusEntries.isEmpty()) "(clean)" else statusEntries.joinToString("\n") { "${it.code.padEnd(2)} ${it.path}" }

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
        style = StyleSet.parse("left:0; top:0; right:${safeWidth - 1}; bottom:0"),
        id = "git-header",
    )

    val statusBoxHeight = (safeHeight / 3).coerceAtLeast(5)
    val statusBox = DOMNode(
        tag = "git-status",
        text = statusText,
        style = StyleSet.parse("left:0; top:1; right:${safeWidth - 1}; bottom:${statusBoxHeight}"),
        id = "git-status",
    )

    val splitter1 = HorizontalSplitter(
        style = StyleSet.parse("left:0; top:${statusBoxHeight + 1}; right:${safeWidth - 1}; bottom:${statusBoxHeight + 1}")
    )

    val messageBoxTop = statusBoxHeight + 2
    val messageBoxHeight = 5
    val messageLabel = DOMNode(
        tag = "git-message-label",
        text = "${workspaceRoot}\nMessage",
        style = StyleSet.parse("left:0; top:${messageBoxTop}; right:${safeWidth - 1}; bottom:${messageBoxTop}"),
        id = "git-message-label",
    )

    val messageArea = Textarea(
        text = message,
        style = StyleSet.parse("left:0; top:${messageBoxTop + 1}; right:${safeWidth - 1}; bottom:${messageBoxTop + messageBoxHeight}"),
        onChange = { _, new -> setMessage(new) }
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
        style = StyleSet.parse("left:${safeWidth - 3}; top:${commitsTop}; right:${safeWidth - 1}; bottom:${safeHeight - 1}"),
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
            messageLabel,
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
        val statText = "Pos:$splitterPos,drag:$dragging,StartX:$dragStartX,Split:$dragStartSplit"

        // --- Layout math
        val minPanelWidth = 20
        val maxPanelWidth = (cols - 4).coerceAtLeast(minPanelWidth)
        val clampedSplit = splitterPos.coerceIn(minPanelWidth, maxPanelWidth)
        val mainHeight = rows - 2
        val tabContentWidth = (clampedSplit - 5).coerceAtLeast(1)
        val workspaceRoot = System.getProperty("user.dir") ?: "."

        // --- Components
        val header = DOMNode(
            tag = "header",
            id = "header",
            text = " Kotlin TUI Demo (q=quit) ",
            style = StyleSet.parse("left:0; top:0; right:${cols - 1}; bottom:0"),
            onMouseMove = {ev ->
                setStatus("$statText,header,hover,x${ev.x},y${ev.y}")
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
                                style = StyleSet.parse("left:0; top:0; right:${tabContentWidth - 1}; bottom:${mainHeight - 1}"),
                                onFileSelected = { entry ->
                                    setStatus("File Selected ${entry.fullPath}")
                                    setSelectedFileTreeEntry(entry)
                                },
                                onFolderSelected = { entry ->
                                    setStatus("Folder Selected ${entry.fullPath}")
                                    setSelectedFileTreeEntry(entry)
                                }
                            )
                        },
                        "Git" to { _ ->
                            GitComponent(
                                tree = tree,
                                workspaceRoot = workspaceRoot,
                                style = StyleSet.parse("left:0; top:0; right:${tabContentWidth - 1}; bottom:${mainHeight - 1}")
                            )
                        },
                        "Logs" to { _ ->
                            DOMNode(
                                tag = "logs-tab",
                                text = "Logs\n[recent events]",
                                style = StyleSet.parse("left:0; top:0; right:${tabContentWidth - 1}; bottom:${mainHeight - 1}"),
                                id = "logs-tab",
                            )
                        }
                    ),
                    onTabChanged = { name -> setStatus("Tab -> $name") }
                )
            )
        )

        // Splitter with simple drag logic in-place
        val splitter = DOMNode(
            tag = "splitter",
            id = "splitter",
            text = "⣿\n".repeat(mainHeight),
            style = StyleSet.parse("left:${clampedSplit - 1}; top:0; right:${clampedSplit}; bottom:${mainHeight - 1}"),
            onMouseDown = { ev ->
                val mx = ev.x ?: return@DOMNode
                setDragging(true)
                setDragStartX(mx)
                setDragStartSplit(clampedSplit)
                setStatus("Splitter grab @${ev.x},${ev.y}")
            },
        )

        val content = DOMNode(
            tag = "content",
            id = "content",
            text = "Editor\n[placeholder]",
            style = StyleSet.parse("left:${clampedSplit}; top:0; right:${cols - 1}; bottom:${mainHeight - 1}"),
            onMouseMove = {ev ->
                setStatus("$statText,content,hover,x${ev.x},y${ev.y}")
            }
        )

        val statusBar = DOMNode(
            tag = "footer",
            id = "footer",
            text = "$status | split=$clampedSplit drag=$dragging",
            style = StyleSet.parse("left:0; top:${rows - 1}; right:${cols - 1}; bottom:${rows - 1}"),
            onMouseMove = {ev ->
                setStatus("$statText,footer,hover,x${ev.x},y${ev.y}")
            }
        )

        val mainArea = DOMNode(
            tag = "main-area",
            id = "main-area",
            style = StyleSet.parse("left:0; top:1; right:${cols - 1}; bottom:${rows - 2}"),
            children = listOf(sidebar, splitter, content),
            onMouseMove = {ev ->

                val mx = ev.x ?: return@DOMNode
                if (dragging) {
                    val dx = mx - dragStartX
                    setSplitterPos((dragStartSplit + dx).coerceIn(minPanelWidth, maxPanelWidth))
                    setStatus("Splitter drag ${mx},${ev.y}")
                } else {
                    setStatus("$statText,main-area,x${ev.x},y${ev.y}")
                }
            },
            onMouseUp = { ev ->
                setDragging(false)
                // if (dragging) {
                //     val mx = ev.x ?: return@DOMNode
                //     val dx = mx - dragStartX
                //     setSplitterPos((dragStartSplit + dx).coerceIn(minPanelWidth, maxPanelWidth))
                //     setStatus("Splitter release ${mx},${ev.y}")
                // }
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
            val root = applyStyles(rawRoot, styleSheet)
            tree.endFrame()
            lastDom = root

            renderer.clear()
            renderDomTree(renderer, root)
            renderer.flush()

            // Poll a single event (non-blocking) after rendering
            val event = renderer.tryPollEvent()
            if (event != null) {
                if (event.kind == "key_down" &&
                    (event.key == "q" || event.key == "Q" || event.key == "Esc" || event.key == "\u0003")) {
                    renderer.requestExit()
                } else {
                    dispatchEventToDom(lastDom, event, 0, 0)
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
