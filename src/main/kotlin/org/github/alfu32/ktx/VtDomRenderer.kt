package org.github.alfu32.ktx

import org.github.alfu32.ktx.components.BoxComponent
import org.github.alfu32.ktx.context.*
import org.github.alfu32.ktx.vdom.*

/**
 * Renders a DomNode tree into a VtDrawingContext and routes VtEvents to nodes.
 *
 * Layout model:
 *  - Node gets a rect by applying left/top/right/bottom margins relative to parent.
 *  - Missing margins default to 0.
 *  - The node fills the remaining space in parent after margins.
 */
class DomRenderer(
    private val ctx: VtDrawingContext,
    var root: DomNode
) : VtEventListener {

    private val hitList = mutableListOf<Pair<DomNode, Rect>>()
    private val defaultComponent: DomComponent = BoxComponent

    fun frame() {
        hitList.clear()
        val rootRect = Rect(
            x = 0,
            y = 0,
            width = ctx.windowWidth,
            height = ctx.windowHeight
        )

        layoutNode(root, rootRect)

        ctx.clear()
        drawNode(root)
        ctx.flush()
    }

    // Exposed so components can draw children
    internal fun renderChildren(parent: DomNode) {
        for (child in parent.children) {
            drawNode(child)
        }
    }

    // Public hit-test used by your controller
    fun findTopMostNodeAt(x: Int, y: Int): DomNode? =
        hitList.lastOrNull { (_, rect) -> rect.contains(x, y) }?.first

    // ---------- layout ----------

    private fun layoutNode(node: DomNode, parentRect: Rect) {
        val comp = node.component ?: defaultComponent
        val rect = comp.layout(node, parentRect, ctx)

        node.bounds = rect
        hitList += node to rect

        // Children use node's rect as their parent rect (component can encode more logic if desired)
        for (child in node.children) {
            layoutNode(child, rect)
        }
    }

    // ---------- drawing ----------

    private fun drawNode(node: DomNode) {
        val comp = node.component ?: defaultComponent
        comp.render(node, ctx, this)
    }

    // ---------- events ----------

    override fun onEvent(ctxFrom: VtDrawingContext, event: VtEvent) {
        when (event) {
            is VtEvent.Mouse -> handleMouse(event)
            is VtEvent.Key -> handleKey(event)
            is VtEvent.Resize -> handleResize(event)
        }
    }

    private fun handleMouse(ev: VtEvent.Mouse) {
        val hit = hitList.lastOrNull { (_, rect) -> rect.contains(ev.x, ev.y) } ?: return
        val (node, rect) = hit

        val comp = node.component ?: defaultComponent
        if (comp.handleEvent(node, ev, ctx)) {
            // component fully handled it
            return
        }

        val localX = ev.x - rect.x
        val localY = ev.y - rect.y
        val domEvent = DomMouseEvent(
            localX = localX,
            localY = localY,
            globalX = ev.x,
            globalY = ev.y,
            original = ev
        )

        when (ev.kind) {
            VtMouseEventKind.Press -> node.onMouseDown?.invoke(domEvent)
            VtMouseEventKind.Release -> node.onMouseUp?.invoke(domEvent)
            VtMouseEventKind.Move,
            VtMouseEventKind.Drag -> node.onMouseMove?.invoke(domEvent)
        }
    }

    private fun handleKey(ev: VtEvent.Key) {
        // Simple model: send keys to root node's component first
        val comp = root.component ?: defaultComponent
        if (comp.handleEvent(root, ev, ctx)) return

        root.onKey?.invoke(DomKeyEvent(ev.key, ev))
    }

    private fun handleResize(ev: VtEvent.Resize) {
        val comp = root.component ?: defaultComponent
        if (comp.handleEvent(root, ev, ctx)) return

        val domEvent = DomResizeEvent(ev.width, ev.height, ev)
        broadcastResize(root, domEvent)
    }

    private fun broadcastResize(node: DomNode, ev: DomResizeEvent) {
        node.onWindowResize?.invoke(ev)
        for (child in node.children) {
            broadcastResize(child, ev)
        }
    }
}

