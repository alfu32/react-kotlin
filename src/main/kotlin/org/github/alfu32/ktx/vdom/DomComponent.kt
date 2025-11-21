package org.github.alfu32.ktx.vdom

import org.github.alfu32.ktx.DomRenderer
import org.github.alfu32.ktx.context.VtDrawingContext
import org.github.alfu32.ktx.context.VtEvent

interface DomComponent {
    /**
     * Compute this node's Rect inside parentRect.
     * Implementations can inspect node.layout, node.children, ctx.windowWidth/Height, etc.
     */
    fun layout(node: DomNode, parentRect: Rect, ctx: VtDrawingContext): Rect

    /**
     * Render this node. For children, call renderer.renderChildren(node).
     */
    fun render(node: DomNode, ctx: VtDrawingContext, renderer: DomRenderer)

    /**
     * Intercept events targeted at this node (mouse/key/resize/etc).
     * Return true if the event is fully handled and should not be propagated further.
     */
    fun handleEvent(node: DomNode, event: VtEvent, ctx: VtDrawingContext): Boolean = false
}