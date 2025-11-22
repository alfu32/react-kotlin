package org.github.alfu32.ktx.vdom

import org.github.alfu32.ktx.renderers.VtDomRenderer
import org.github.alfu32.ktx.context.VtDrawingContext

interface DomComponent {
    /**
     * Render this node.
     * Layout (node.bounds) is already computed by DomRenderer.
     */
    fun render(node: DomNode, ctx: VtDrawingContext, renderer: VtDomRenderer)
}