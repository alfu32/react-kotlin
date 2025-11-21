package org.github.alfu32.ktx

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

    // Hit list for mouse: draw-order = insertion order, last is top-most
    private val hitList = mutableListOf<Pair<DomNode, Rect>>()

    /**
     * Render one frame:
     *  - recompute layout from ctx.windowWidth/Height
     *  - clear + draw tree
     */
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

    // ========== Layout ==========

    private fun layoutNode(node: DomNode, parentRect: Rect) {
        val l = node.layout.left ?: 0
        val t = node.layout.top ?: 0
        val r = node.layout.right ?: 0
        val b = node.layout.bottom ?: 0

        val x = parentRect.x + l
        val y = parentRect.y + t
        val width = (parentRect.width - l - r).coerceAtLeast(0)
        val height = (parentRect.height - t - b).coerceAtLeast(0)

        val rect = Rect(x, y, width, height)
        node.bounds = rect
        hitList += node to rect

        for (child in node.children) {
            layoutNode(child, rect)
        }
    }

    // ========== Drawing ==========

    private fun applyStyle(style: DomStyle) {
        ctx.reset()

        if (style.foreground != null) {
            ctx.setColor(style.foreground)
        } else {
            ctx.resetColor()
        }

        if (style.background != null) {
            ctx.setBackgroundColor(style.background)
        } else {
            ctx.resetBackgroundColor()
        }

        if (style.bold) {
            ctx.bold()
        }
        if (style.italic) {
            // ANSI italic; not all terminals support it
            ctx.write("\u001B[3m")
        }
    }

    private fun drawNode(node: DomNode) {
        val rect = node.bounds
        if (rect.width <= 0 || rect.height <= 0) return

        applyStyle(node.style)

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

        for (child in node.children) {
            drawNode(child)
        }
    }

    // ========== Event routing ==========

    override fun onEvent(ctxFrom: VtDrawingContext, event: VtEvent) {
        when (event) {
            is VtEvent.Mouse -> handleMouse(event)
            is VtEvent.Key -> handleKey(event)
            is VtEvent.Resize -> handleResize(event)
        }
    }

    private fun handleMouse(ev: VtEvent.Mouse) {
        val hit = findTopMostNodeAt(ev.x, ev.y) ?: return
        val (node, rect) = hit

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
        // No focus model yet: send keys to root only.
        root.onKey?.invoke(DomKeyEvent(ev.key, ev))
    }

    private fun handleResize(ev: VtEvent.Resize) {
        val domEvent = DomResizeEvent(ev.width, ev.height, ev)
        broadcastResize(root, domEvent)
    }

    private fun broadcastResize(node: DomNode, ev: DomResizeEvent) {
        node.onWindowResize?.invoke(ev)
        for (child in node.children) {
            broadcastResize(child, ev)
        }
    }

    private fun findTopMostNodeAt(x: Int, y: Int): Pair<DomNode, Rect>? {
        for (i in hitList.size - 1 downTo 0) {
            val (node, rect) = hitList[i]
            if (rect.contains(x, y)) return node to rect
        }
        return null
    }
}
