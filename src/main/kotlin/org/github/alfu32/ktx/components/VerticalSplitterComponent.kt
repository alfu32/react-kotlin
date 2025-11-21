package org.github.alfu32.ktx.components

import org.github.alfu32.ktx.AppState
import org.github.alfu32.ktx.DomRenderer
import org.github.alfu32.ktx.context.VtDrawingContext
import org.github.alfu32.ktx.context.VtEvent
import org.github.alfu32.ktx.context.VtMouseEventKind
import org.github.alfu32.ktx.vdom.DomComponent
import org.github.alfu32.ktx.vdom.DomNode
import org.github.alfu32.ktx.vdom.Rect

class VerticalSplitterComponent(
    private val appState: AppState,
    private val minPanelWidth: Int = 40
) : DomComponent {

    override fun layout(node: DomNode, parentRect: Rect, ctx: VtDrawingContext): Rect {
        // node.layout.left is interpreted as current panel width
        val panelWidth = appState.panelWidth.coerceIn(minPanelWidth, (ctx.windowWidth - 2).coerceAtLeast(minPanelWidth))
        val x = parentRect.x + panelWidth
        val y = parentRect.y
        val width = 1
        val height = parentRect.height   // full height of main area
        return Rect(x, y, width, height)
    }

    override fun render(node: DomNode, ctx: VtDrawingContext, renderer: DomRenderer) {
        val rect = node.bounds
        if (rect.width != 1 || rect.height <= 0) return

        // Simple style application
        ctx.reset()
        val style = node.style
        if (style.foreground != null) ctx.setColor(style.foreground) else ctx.resetColor()
        if (style.background != null) ctx.setBackgroundColor(style.background) else ctx.resetBackgroundColor()
        if (style.bold) ctx.bold()
        if (style.italic) ctx.write("\u001B[3m")

        val ch = (node.text.takeIf { it.isNotEmpty() } ?: "│")[0].toString()

        for (yy in rect.y until rect.y + rect.height) {
            ctx.drawText(rect.x, yy, ch)
        }

        // If you ever attach children to the splitter:
        renderer.renderChildren(node)
    }

    override fun handleEvent(node: DomNode, event: VtEvent, ctx: VtDrawingContext): Boolean {
        if (event !is VtEvent.Mouse) return false

        when (event.kind) {
            VtMouseEventKind.Press -> {
                // Start dragging if press in splitter column
                appState.dragging = true
                appState.dragStartMouseX = event.x
                appState.dragStartPanelWidth = appState.panelWidth
                return true
            }
            VtMouseEventKind.Release -> {
                appState.dragging = false
                return true
            }
            VtMouseEventKind.Drag, VtMouseEventKind.Move -> {
                if (appState.dragging) {
                    val dx = event.x - appState.dragStartMouseX
                    val maxPanelWidth = (ctx.windowWidth - 2).coerceAtLeast(minPanelWidth)
                    val newWidth = (appState.dragStartPanelWidth + dx)
                    appState.panelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
                    return true
                }
            }
        }
        return false
    }
}