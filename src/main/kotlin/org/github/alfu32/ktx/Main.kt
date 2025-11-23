package org.github.alfu32.ktx

import java.io.InputStream

//@file:Suppress("UNCHECKED_CAST")

/* =====================================================================
   UNIFIED EVENT STRUCTURE
   ===================================================================== */

data class UIEvent(
    val kind: String,           // e.g. "mouse_down", "key_down", "resize"
    val x: Int? = null,         // mouse coordinates
    val y: Int? = null,
    val button: Int? = null,    // mouse button
    val scrollDelta: Int? = null,
    val key: String? = null,    // keyboard key
    val focusId: String? = null,
    val cols: Int? = null,      // resize cols
    val rows: Int? = null       // resize rows
)

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
    private val rules: Map<String, StyleSet> = emptyMap()
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

/* =====================================================================
   CANVAS RENDERER INTERFACE WITH EVENT POLLING
   ===================================================================== */

interface CanvasRenderer {
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

    fun cols() = cols
    fun rows() = rows
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
    private val cols: Int = 200,
    private val rows: Int = 80
) : CanvasRenderer {

    private fun esc(code: String) {
        output.append("\u001b[$code")
    }

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
            repeat(width) { output.append(" ") }
        }
    }

    override fun drawText(x: Int, y: Int, text: String) {
        esc("${y + 1};${x + 1}H")
        output.append(text)
    }

    override fun setCursorPosition(x: Int, y: Int) {
        esc("${y + 1};${x + 1}H")
    }

    override fun flush() {
        // stdout usually auto-flushes, nothing required
    }

    /* ============================================================
       Event Parsing
       ============================================================ */

    override fun pollEvent(): UIEvent? {
        while (true) {
            val e = tryPollEvent()
            if (e != null) return e
            Thread.sleep(5)
        }
    }

    override fun tryPollEvent(): UIEvent? {
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

        // Mouse Click: <btn;x;yM or <btn;x;ym
        if (s.endsWith("M") || s.endsWith("m")) {
            val body = s.dropLast(1).split(';')
            if (body.size >= 3 && body[0].startsWith("<")) {
                val btn = body[0].drop(1).toIntOrNull() ?: 0
                val x = body[1].toIntOrNull()?.minus(1) ?: 0
                val y = body[2].toIntOrNull()?.minus(1) ?: 0
                val down = s.endsWith("M")
                return if (down)
                    UIEvent("mouse_down", x=x, y=y, button=btn)
                else
                    UIEvent("mouse_up", x=x, y=y, button=btn)
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
        // optional: clear terminal on exit
        // output.append("\u001b[2J\u001b[H")
    }

    fun cols() = cols
    fun rows() = rows
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

    fun cols() = cols
    fun rows() = rows

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

    val children: List<DOMNode> = emptyList()
)

fun Button(
    text: String,
    onClick: ((UIEvent) -> Unit)? = null,
    style: StyleSet = StyleSet()
): DOMNode =
    DOMNode(
        tag = "button",
        text = text,
        style = style,
        onMouseDown = onClick
    )

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
    val x2 = parentX + right
    val y2 = parentY + bottom

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

    when (event.kind) {
        "mouse_down"   -> if (inside) node.onMouseDown?.invoke(event)
        "mouse_up"     -> if (inside) node.onMouseUp?.invoke(event)
        "mouse_move"   -> if (inside) node.onMouseMove?.invoke(event)
        "mouse_scroll" -> if (inside) node.onMouseScroll?.invoke(event)
        "key_down"     -> node.onKeyDown?.invoke(event)
        "key_up"       -> node.onKeyUp?.invoke(event)
        "focus_gained" -> node.onFocusGained?.invoke(event)
        "focus_lost"   -> node.onFocusLost?.invoke(event)
        "resize"       -> node.onResize?.invoke(event)
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

    val width  = x2 - x1
    val height = y2 - y1

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

    dom.text?.let { renderer.drawText(x1, y1, it) }

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
        tag = "div",
        children = values.map { v -> counterComponent(tree, key = v.toString()) }
    )

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

        // --- Layout math
        val minPanelWidth = 20
        val maxPanelWidth = (cols - 4).coerceAtLeast(minPanelWidth)
        val clampedSplit = splitterPos.coerceIn(minPanelWidth, maxPanelWidth)
        val mainHeight = rows - 2

        // --- Components
        val header = DOMNode(
            tag = "header",
            id = "header",
            text = " Kotlin TUI Demo (q=quit) ",
            style = StyleSet.parse("left:0; top:0; right:${cols - 1}; bottom:0; fg:#bebebb; bg:#223388")
        )

        val sidebar = DOMNode(
            tag = "div",
            id = "sidebar",
            text = "File tree\n[placeholder]",
            style = StyleSet.parse("left:0; top:0; right:${clampedSplit - 1}; bottom:${mainHeight - 1}; fg:#bebebb; bg:#636fab"),
            onMouseDown = { ev ->
                setStatus("Sidebar click @${ev.x},${ev.y}")
            }
        )

        // Splitter with simple drag logic in-place
        val splitter = DOMNode(
            tag = "div",
            id = "splitter",
            text = "│",
            style = StyleSet.parse("left:$clampedSplit; top:0; right:$clampedSplit; bottom:${mainHeight - 1}; fg:#bebebb; bg:#808080"),
            onMouseDown = { ev ->
                val mx = ev.x ?: return@DOMNode
                setDragging(true)
                setDragStartX(mx)
                setDragStartSplit(clampedSplit)
                setStatus("Splitter grab @${ev.x},${ev.y}")
            },
            onMouseMove = { ev ->
                val mx = ev.x ?: return@DOMNode
                if (dragging) {
                    val dx = mx - dragStartX
                    setSplitterPos((dragStartSplit + dx).coerceIn(minPanelWidth, maxPanelWidth))
                    setStatus("Splitter drag ${mx},${ev.y}")
                }
            },
            onMouseUp = { ev ->
                val mx = ev.x ?: return@DOMNode
                val dx = mx - dragStartX
                setSplitterPos((dragStartSplit + dx).coerceIn(minPanelWidth, maxPanelWidth))
                setDragging(false)
                setStatus("Splitter release ${mx},${ev.y}")
            }
        )

        val content = DOMNode(
            tag = "div",
            id = "content",
            text = "Editor\n[placeholder]",
            style = StyleSet.parse("left:${clampedSplit + 1}; top:0; right:${cols - 1}; bottom:${mainHeight - 1}; fg:#bebebb; bg:#182460"),
            onMouseDown = { ev ->
                setStatus("Content click @${ev.x},${ev.y}")
            }
        )

        val statusBar = DOMNode(
            tag = "footer",
            id = "status",
            text = "$status | split=$clampedSplit drag=$dragging",
            style = StyleSet.parse("left:0; top:${rows - 1}; right:${cols - 1}; bottom:${rows - 1}; fg:#bebebb; bg:#4d4d4d")
        )

        val mainArea = DOMNode(
            tag = "div",
            id = "main-area",
            style = StyleSet.parse("left:0; top:1; right:${cols - 1}; bottom:${rows - 2}"),
            children = listOf(sidebar, splitter, content)
        )

        // Root composes everything
        DOMNode(
            tag = "root",
            id = "root",
            style = StyleSet.parse("left:0; top:0; right:${cols - 1}; bottom:${rows - 1}"),
            children = listOf(header, mainArea, statusBar)
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

fun runApp(renderer: CanvasRenderer, rootFn: (ComponentTreeManager) -> DOMNode) : AppContext{
    val appContext = AppContext()
    val tree = ComponentTreeManager()
    var lastDom: DOMNode = DOMNode("empty")

    renderer.enableMouseTracking()
    renderer.hideCursor()

    val startTime = System.currentTimeMillis()
    var deadCycles = 0
    var frame: ULong = 0.toULong()

    try {
        while (true) {
            frame+=1.toULong()
            /* ============================================================
               ABSOLUTE FAILSAFE #1 — renderer exit flag
               ============================================================ */
            if (!renderer.isRunning()) {
                break
            }

            /* ============================================================
               POLL EVENT (non-blocking first)
               ============================================================ */
            val event = renderer.tryPollEvent() ?: renderer.pollEvent()

            if (event != null) {

                /* ============================================================
                   APPLICATION EXIT TRIGGERS
                   ============================================================ */
                if (event.kind == "key_down") {
                    when (event.key) {
                        "q", "Q", "Esc", "\u0003" /* Ctrl+C */ -> {
                            renderer.requestExit()
                            continue
                        }
                    }
                }

                dispatchEventToDom(lastDom, event, 0, 0)
            }

            /* ============================================================
               RENDER FRAME
               ============================================================ */
            tree.beginFrame()
            val root = rootFn(tree)
            tree.endFrame()
            lastDom = root

            renderer.clear()
            renderDomTree(renderer, root)
            renderer.flush()

            /* ============================================================
               ABSOLUTE FAILSAFE #2 — infinite-loop protection
               ============================================================ */
            if (event == null) deadCycles++ else deadCycles = 0
            if (deadCycles > 5000) {        // configurable
                renderer.requestExit()
            }

            /* ============================================================
               ABSOLUTE FAILSAFE #3 — time-based emergency exit
               ============================================================ */
            if (System.currentTimeMillis() - startTime > 48 * 60 * 60 * 1000L) {
                // safety: 48 hours uptime max
                renderer.requestExit()
            }

            /* ============================================================
               BREAK ON EXIT REQUEST
               ============================================================ */
            if (!renderer.isRunning()) break
            appContext.onFrame(frame)
        }
        appContext.onExit()
    }catch (x: Throwable){
        println(x)
        appContext.onError(x)
    }
    finally {
        // CLEANUP GUARANTEED
        renderer.disableMouseTracking()
        renderer.showCursor()
        renderer.resetAttributes()
        renderer.shutdown()
        appContext.onExit()
    }
    return appContext
}
/* =====================================================================
   APPLICATION ENTRY POINT
   ===================================================================== */

fun main() {
    val renderer = StringSnapshotRenderer(cols = 120, rows = 40).apply {
        isRunning()
    }
    runApp(renderer){ tree : ComponentTreeManager ->
        App(tree, renderer.cols(), renderer.rows())
    }.apply {
        onExit={
            println("DONE")
            println(renderer.snapshot())
        }
        onError={ println(it)}
        onFrame={ println("FRAME: $it")}
    }
}


/* =====================================================================
   END OF FILE
   ===================================================================== */
