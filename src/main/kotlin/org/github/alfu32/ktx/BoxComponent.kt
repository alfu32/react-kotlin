package org.github.alfu32.ktx

import org.github.alfu32.ktx.context.VtDrawingContext
import org.github.alfu32.ktx.renderers.VtDomRenderer
import org.github.alfu32.ktx.vdom.DomComponent
import org.github.alfu32.ktx.vdom.DomNode
import org.github.alfu32.ktx.vdom.DomStyle

object BoxComponent : DomComponent {

    override fun render(node: DomNode, ctx: VtDrawingContext, renderer: VtDomRenderer) {
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

        // Draw text (clipped to rect)
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

        // Draw children on top
        renderer.renderChildren(node)
    }

    private fun applyStyle(ctx: VtDrawingContext, style: DomStyle) {
        ctx.reset()
        if (style.foreground != null) ctx.setColor(style.foreground) else ctx.resetColor()
        if (style.background != null) ctx.setBackgroundColor(style.background) else ctx.resetBackgroundColor()
        if (style.bold) ctx.bold()
        if (style.italic) ctx.write("\u001B[3m")
    }
}