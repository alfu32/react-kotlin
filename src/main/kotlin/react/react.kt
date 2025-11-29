package react

import react.renderer.AnsiCanvasRenderer
import react.renderer.CanvasRenderer
import java.time.Instant

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

    for (child in node.children) {
        val childHit = findTopmostHit(child, event, x1, y1)
        if (childHit != null) return childHit
    }
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

fun renderDomTree(renderer: CanvasRenderer, dom: DOMNode, parentX: Int = 0, parentY: Int = 0) {
    if (dom.visible) {
        val left = dom.style.left ?: 0
        val top = dom.style.top ?: 0
        val right = dom.style.right ?: 0
        val bottom = dom.style.bottom ?: 0

        val x1 = parentX + left
        val y1 = parentY + top
        val x2 = parentX + right
        val y2 = parentY + bottom

        val width = (x2 - x1 + 1).coerceAtLeast(1)
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
}

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

/* =====================================================================
   RENDERING ENGINE
   ===================================================================== */
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
            val d0 = Instant.now().nano.toLong()
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
            val du = (Instant.now().nano.toLong() - d0)/1000/1000
            if(du<25) {
                // sleep(25.toLong() - du)
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

// ============ Terminal raw mode helpers ============
fun runCommand(vararg cmd: String): String? = try {
    ProcessBuilder(*cmd)
        .redirectErrorStream(true)
        .start()
        .inputStream.bufferedReader().use { it.readText() }
} catch (_: Exception) { null }
