package org.github.alfu32.ktx.components

import org.github.alfu32.ktx.DomRenderer
import org.github.alfu32.ktx.context.VtDrawingContext
import org.github.alfu32.ktx.context.VtEvent
import org.github.alfu32.ktx.vdom.DomComponent
import org.github.alfu32.ktx.vdom.DomNode
import org.github.alfu32.ktx.vdom.Rect

class DropdownComponent(
    private val options: List<String>,
    private var selectedIndex: Int = 0,
    val onChange: (String) -> Unit
) : DomComponent {

    private var expanded = false

    override fun layout(node: DomNode, parentRect: Rect, ctx: VtDrawingContext): Rect {
        val height = if (expanded) 1 + options.size else 1
        val width = options.maxOfOrNull { it.length }?.coerceAtLeast(3) ?: 3
        val x = parentRect.x + (node.layout.left ?: 0)
        val y = parentRect.y + (node.layout.top ?: 0)
        return Rect(x, y, width, height)
    }

    override fun render(node: DomNode, ctx: VtDrawingContext, renderer: DomRenderer) {
        val rect = node.bounds
        if (rect.width <= 0 || rect.height <= 0) return

        ctx.reset()
        // draw box + current value + optional list below...
        // (omitted for brevity)
    }

    override fun handleEvent(node: DomNode, event: VtEvent, ctx: VtDrawingContext): Boolean {
        when (event) {
            is VtEvent.Mouse -> {
                // toggle expanded, change selection based on click
            }
            is VtEvent.Key -> {
                // arrow up/down to change selection, Enter to commit
            }
            else -> {}
        }
        return true
    }
}
