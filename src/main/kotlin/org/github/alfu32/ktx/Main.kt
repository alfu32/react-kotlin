package org.github.alfu32.ktx

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
   END OF FILE
   ===================================================================== */
