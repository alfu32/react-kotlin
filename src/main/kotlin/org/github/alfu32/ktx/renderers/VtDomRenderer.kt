package org.github.alfu32.ktx.renderers

import org.github.alfu32.ktx.BoxComponent
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
class VtDomRenderer(
    private val ctx: VtDrawingContext,
    var root: DomNode
) : VtEventListener {

    private val hitList = mutableListOf<Pair<DomNode, Rect>>()

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
        val l = node.layout.left
        val t = node.layout.top
        val r = node.layout.right
        val b = node.layout.bottom

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
        val hit = hitList.lastOrNull { (_, rect) -> rect.contains(ev.x, ev.y) } ?: return
        val (node, rect) = hit

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
            VtMouseEventKind.Press   -> node.onMouseDown?.invoke(domEv)
            VtMouseEventKind.Release -> node.onMouseUp?.invoke(domEv)
            VtMouseEventKind.Move,
            VtMouseEventKind.Drag    -> node.onMouseMove?.invoke(domEv)
        }
    }

    private fun handleKey(ev: VtEvent.Key) {
        // Simple: send to root only for now
        root.onKey?.invoke(DomKeyEvent(ev.key, ev))
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
}