package org.github.alfu32.ktx.components

import org.github.alfu32.ktx.DomRenderer
import org.github.alfu32.ktx.context.VtDrawingContext
import org.github.alfu32.ktx.context.VtEvent
import org.github.alfu32.ktx.vdom.DomComponent
import org.github.alfu32.ktx.vdom.DomNode
import org.github.alfu32.ktx.vdom.DomStyle
import org.github.alfu32.ktx.vdom.Rect

object BoxComponent : DomComponent {

    override fun layout(node: DomNode, parentRect: Rect, ctx: VtDrawingContext): Rect {
        val l = node.layout.left ?: 0
        val t = node.layout.top ?: 0
        val r = node.layout.right ?: 0
        val b = node.layout.bottom ?: 0

        val x = parentRect.x + l
        val y = parentRect.y + t
        val width = (parentRect.width - l - r).coerceAtLeast(0)
        val height = (parentRect.height - t - b).coerceAtLeast(0)

        return Rect(x, y, width, height)
    }

    override fun render(node: DomNode, ctx: VtDrawingContext, renderer: DomRenderer) {
        val rect = node.bounds
        if (rect.width <= 0 || rect.height <= 0) return

        applyStyle(ctx, node.style)

        // Fill background if any
        if (node.style.background != null && rect.width > 0 && rect.height > 0) {
            for (yy in rect.y until rect.y + rect.height) {
                ctx.setCursorPosition(rect.x, yy)
                var remaining = rect.width
                while (remaining > 0) {
                    val chunk = minOf(remaining, 64)
                    ctx.write(" ".repeat(chunk))
                    remaining -= chunk
                }
            }
        }

        // Text
        if (node.text.isNotEmpty()) {
            val lines = node.text.split('\n')
            var y = rect.y
            for (line in lines) {
                if (y >= rect.y + rect.height) break
                if (rect.width <= 0) break
                val clipped = if (line.length > rect.width) line.substring(0, rect.width) else line
                ctx.drawText(rect.x, y, clipped)
                y++
            }
        }

        // Children
        renderer.renderChildren(node)
    }

    override fun handleEvent(node: DomNode, event: VtEvent, ctx: VtDrawingContext): Boolean {
        // Default component does not intercept; let renderer dispatch to node handlers
        return false
    }

    private fun applyStyle(ctx: VtDrawingContext, style: DomStyle) {
        ctx.reset()
        if (style.foreground != null) ctx.setColor(style.foreground) else ctx.resetColor()
        if (style.background != null) ctx.setBackgroundColor(style.background) else ctx.resetBackgroundColor()
        if (style.bold) ctx.bold()
        if (style.italic) ctx.write("\u001B[3m")
    }
}