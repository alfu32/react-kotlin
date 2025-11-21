package org.github.alfu32.ktx

import org.github.alfu32.ktx.context.*
import org.github.alfu32.ktx.vdom.*

private const val MIN_PANEL_WIDTH = 40

data class AppState(
    var panelWidth: Int = MIN_PANEL_WIDTH,
    var dragging: Boolean = false,
    var dragStartMouseX: Int = 0,
    var dragStartPanelWidth: Int = MIN_PANEL_WIDTH,
    var statusText: String = ""
)

/**
 * Application controller: handles events, layout, and status updates.
 */
class AppController(
    private val ctx: AnsiVtDrawingContext,
    private val renderer: DomRenderer,
    private val state: AppState,
    private val titleBar: DomNode,
    private val mainArea: DomNode,
    private val leftPanel: DomNode,
    private val separator: DomNode,
    private val contentArea: DomNode,
    private val statusBar: DomNode
) : VtEventListener {

    override fun onEvent(context: VtDrawingContext, event: VtEvent) {
        when (event) {
            is VtEvent.Mouse -> handleMouse(event)
            is VtEvent.Key -> handleKey(event)
            is VtEvent.Resize -> handleResize(event)
        }

        // After app-level handling, pass to DOM to invoke node callbacks:
        renderer.onEvent(context, event)
    }

    private fun handleMouse(ev: VtEvent.Mouse) {
        val targetNode = renderer.findTopMostNodeAt(ev.x, ev.y)
        val targetId = targetNode?.id ?: "<none>"

        state.statusText =
            "MOUSE ${ev.kind} x=${ev.x} y=${ev.y} target=$targetId btn=${ev.button}"

        // Drag behavior only cares about separator
        val isSeparatorTarget = (targetNode === separator)

        when (ev.kind) {
            VtMouseEventKind.Press -> {
                if (isSeparatorTarget) {
                    state.dragging = true
                    state.dragStartMouseX = ev.x
                    state.dragStartPanelWidth = state.panelWidth
                }
            }
            VtMouseEventKind.Release -> {
                state.dragging = false
            }
            VtMouseEventKind.Drag, VtMouseEventKind.Move -> {
                if (state.dragging) {
                    val dx = ev.x - state.dragStartMouseX
                    val newWidth = (state.dragStartPanelWidth + dx)
                    val maxPanelWidth = (ctx.windowWidth - 2).coerceAtLeast(MIN_PANEL_WIDTH)
                    state.panelWidth = newWidth.coerceIn(MIN_PANEL_WIDTH, maxPanelWidth)
                }
            }
        }
    }

    private fun handleKey(ev: VtEvent.Key) {
        state.statusText = "KEY type=${ev.key.type} ch=${ev.key.ch ?: ' '} ctrl=${ev.key.ctrl} alt=${ev.key.alt}"
        // Quit on 'q'
        if (ev.key.type == VtKeyType.Character && ev.key.ch == 'q') {
            ctx.stop()
        }
    }

    private fun handleResize(ev: VtEvent.Resize) {
        // Keep panel width sane on resize
        val maxPanelWidth = (ev.width - 2).coerceAtLeast(MIN_PANEL_WIDTH)
        state.panelWidth = state.panelWidth.coerceIn(MIN_PANEL_WIDTH, maxPanelWidth)
        state.statusText = "RESIZE ${ev.width}x${ev.height}"
    }

    /**
     * Called once per frame by AnsiVtDrawingContext.onFrame.
     * Computes layout + pushes status bar text + renders.
     */
    fun frame() {
        val w = ctx.windowWidth
        val h = ctx.windowHeight
        if (w <= 0 || h <= 0) return

        // --- vertical structure: title, main, status ---

        titleBar.layout = DomLayout(
            left = 0,
            top = 0,
            right = 0,
            bottom = h - 1      // height 1
        )

        mainArea.layout = DomLayout(
            left = 0,
            top = 1,
            right = 0,
            bottom = 1          // height = h - 2
        )

        statusBar.layout = DomLayout(
            left = 0,
            top = h - 1,
            right = 0,
            bottom = 0          // height 1 at bottom
        )

        // --- horizontal structure inside mainArea ---

        val panelWidth = state.panelWidth.coerceIn(MIN_PANEL_WIDTH, (w - 2).coerceAtLeast(MIN_PANEL_WIDTH))
        val separatorX = panelWidth
        val contentLeft = panelWidth + 1

        leftPanel.layout = DomLayout(
            left = 0,
            top = 0,
            right = w - panelWidth,
            bottom = 0
        )

        separator.layout = DomLayout(
            left = separatorX,
            top = 0,
            right = w - (separatorX + 1),
            bottom = 0
        )

        contentArea.layout = DomLayout(
            left = contentLeft,
            top = 0,
            right = 0,
            bottom = 0
        )

        // Status bar text
        statusBar.text = state.statusText

        renderer.frame()
    }
}