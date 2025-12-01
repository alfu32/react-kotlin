package react

import react.renderer.AnsiCanvasRenderer
import react.renderer.CanvasRenderer
import java.time.Instant
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean
import react.renderer.Draw
import kotlin.math.roundToInt

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

class Meter(
    private val name: String,
    private val unit: String,
    private val timing: Int = 1024
){
    private var minVal: Int = Int.MAX_VALUE
    private var maxVal: Int = Int.MIN_VALUE
    private var avgVal: Double = 0.0
    private var samples: Long = 0

    fun collect(value: Int, frame: Long) {
        samples++
        if (value < minVal) minVal = value
        if (value > maxVal) maxVal = value

        // Simple running average across all collected samples.
        val prev = samples - 1
        avgVal = if (prev <= 0) {
            value.toDouble()
        } else {
            ((avgVal * prev) + value.toDouble()) / samples.toDouble()
        }
    }
    fun percentageInt():Int{
        if (maxVal == minVal) {
            return 1
        }
        val avg = avgVal.roundToInt()
        return avg.minus(minVal).times(100).div(maxVal.minus(minVal)).coerceAtLeast(1).coerceAtMost(100)
    }

    override fun toString(): String =
        "%s[%4d,%4d,%4d]%s".format(name, minVal, avgVal.roundToInt(), maxVal, unit)
}

private class PerfCounters(
    private val timing: Int = 1024
) {
    private val osBean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean

    private var fps: Double = 0.0
    private var fpsWindowStart = System.nanoTime()
    private var fpsFrameCount = 0

    private var cpuPercent = 0.0
    private var cpuLastWall = System.nanoTime()
    private var cpuLastProc = osBean?.processCpuTime ?: 0L

    private val fpsMeter = Meter("FPS", "f/s", 1)
    private val memMeter = Meter("Mem", "MB", timing)
    private val cpuMeter = Meter("CPU", "%", timing)

    fun collect(frame: Long) {
        fpsFrameCount++

        val now = System.nanoTime()
        if (now - fpsWindowStart >= 1_000_000_000L) {
            fps = fpsFrameCount.toDouble() * 1_000_000_000.0 / (now - fpsWindowStart).toDouble()
            fpsFrameCount = 0
            fpsWindowStart = now
            fpsMeter.collect(fps.roundToInt(), frame)
        }

        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        memMeter.collect(usedMb.toInt(), frame)

        if (osBean != null) {
            val procNow = osBean.processCpuTime
            val wallNow = now
            val wallDelta = wallNow - cpuLastWall
            val cpuDelta = procNow - cpuLastProc
            if (wallDelta > 0) {
                val cores = osBean.availableProcessors.toDouble().coerceAtLeast(1.0)
                cpuPercent = (cpuDelta.toDouble() / wallDelta.toDouble()) * 100.0 / cores
            }
            cpuLastWall = wallNow
            cpuLastProc = procNow
            cpuMeter.collect(cpuPercent.roundToInt(), frame)
        }
    }

    fun render(renderer: CanvasRenderer, nodeCount: Int, hits: List<DOMNode>, visible: Boolean) {
        if (!visible) return
        val fps = fpsMeter.toString()
        val node = nodeCount.toString().padStart(4, ' ')
        val mem = memMeter.toString()
        val cpu = cpuMeter.toString()
        val y = (renderer.rows() - 1).coerceAtLeast(1)-1
        var x = 0
        // Draw(renderer){
        //     text(0,y, "$fps $node $mem $cpu".padEnd(renderer.cols()),"bg:#444400;fg:#aaaaaa")
        //     text(0,y+1, hits.joinToString(",").padEnd(renderer.cols()),"bg:#DDDD22;fg:#aaaaaa")
        // }
        Draw(renderer){
            try{
                meter(x, y, fpsMeter.percentageInt(), fps+"  ", "bg:#333333;fg:#aaaaaa")
            }catch (e: Exception){}
            x += fps.length + 1
            try{
                meter(x, y, memMeter.percentageInt(), mem+"  ", "bg:#333333;fg:#aaaaaa")
            }catch (e: Exception){}
            x += mem.length + 1
            try{
                meter(x, y, cpuMeter.percentageInt(), cpu+"  ", "bg:#333333;fg:#aaaaaa")
            }catch (e: Exception){}
            x += cpu.length + 1
            // rect(ContentBox(y,0,y+1,renderer.cols()))
            text(x, y, " Nodes: $node".padEnd(renderer.cols()), "bg:#333333;fg:#aaaaaa")
            text(0,y+1, hits
                .joinToString(","){node ->
                    "${node.id}${node.boundingBox()}"
                }
                .padEnd(renderer.cols()),"bg:#333333;fg:#aaaaaa")
        }
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

private fun collectHitNodes(node: DOMNode, event: UIEvent, parentX: Int, parentY: Int, hits: MutableList<DOMNode>) {
    val x = event.x ?: return
    val y = event.y ?: return

    val left = node.style.left ?: 0
    val top = node.style.top ?: 0
    val right = node.style.right ?: 0
    val bottom = node.style.bottom ?: 0

    val x1 = parentX + left
    val y1 = parentY + top
    val x2 = parentX + right + 1
    val y2 = parentY + bottom + 1

    for (child in node.children) {
        collectHitNodes(child, event, x1, y1, hits)
    }
    if (x in x1 until x2 && y in y1 until y2 && node.id != null) {
        hits.add(node)
    }
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

fun renderDomTree(renderer: CanvasRenderer, dom: DOMNode, parentX: Int = 0, parentY: Int = 0): Int {
    var count = 0
    if (dom.visible) {
        count++
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
            // renderer.setBackgroundColor(33,33,33)
            // renderer.setColor(225,33,33)
            // renderer.drawText(if(x2<180)x2 else x2-32,y1,"${dom.tag},${dom.boundingBox()}")
            // renderer.resetAttributes()
        }

        for (child in dom.children)
            count += renderDomTree(renderer, child, x1, y1)
    }
    return count
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
    rootFn: (ComponentTreeManager, Int, Int) -> DOMNode
) : AppContext {
    val appContext: AppContext = AppContext()
    val tree = ComponentTreeManager()
    var lastDom: DOMNode = DOMNode("empty",)
    var focusedId: String? = null
    var lastHits: List<DOMNode> = emptyList()
    val perf = PerfCounters()
    var debugVisible = false

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
            val effectiveRows = (renderer.rows() - if (debugVisible) 2 else 0).coerceAtLeast(1)
            val rawRoot = rootFn(tree, renderer.cols(), effectiveRows)
            clearFocus(rawRoot)
            focusedId?.let { setFocus(rawRoot, it) }
            val root = applyStyles(rawRoot, styleSheet)
            tree.endFrame()
            lastDom = root

            renderer.clear()
            val nodeCount = renderDomTree(renderer, root)

            // Debug toggle + panel drawing
            val toggleLabel = "Debug"
            val toggleX = (renderer.cols() - toggleLabel.length).coerceAtLeast(100)
            val toggleY = (renderer.rows() - 1 - (if(debugVisible) -0 else 0)).coerceAtLeast(0)
            try{
                if (debugVisible) {
                    perf.collect(frame)
                }
                Draw(renderer) {
                    perf.render(renderer, nodeCount, lastHits, debugVisible)
                    renderer.setColor(190,25,10)
                    renderer.drawText(toggleX, toggleY, toggleLabel,)
                }
            }catch(e:Exception){}
            renderer.flush()

            // Poll a single event (non-blocking) after rendering
            val event = renderer.tryPollEvent()
            if (event != null) {
                if (event.kind == "mouse_down" && event.x != null && event.y != null) {
                    val toggleX = renderer.cols() - 5
                    val toggleY = renderer.rows() - 1
                    if (event.x >= toggleX && event.y >= toggleY) {
                        debugVisible = !debugVisible
                        continue
                    }
                }
                if ((event.key == "Esc") || (event.ctrl && event.key == "q")|| (event.key == "~")) {
                    renderer.requestExit()
                } else {
                    dispatchEventToDom(lastDom, event, 0, 0)
                    if (event.x != null && event.y != null) {
                        val hitNodes = mutableListOf<DOMNode>()
                        collectHitNodes(lastDom, event, 0,0,hitNodes)
                        lastHits = hitNodes.map { it }
                        val topmost = hitNodes.firstOrNull()
                        if (topmost?.id != null) {
                            focusedId = topmost.id
                        }
                    } else {
                        lastHits = emptyList()
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
