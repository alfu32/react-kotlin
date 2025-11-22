package org.github.alfu32.ktx

import org.github.alfu32.ktx.color.VtColor
import org.github.alfu32.ktx.context.*
import org.github.alfu32.ktx.vdom.*
import org.github.alfu32.ktx.renderers.VtDomRenderer

private const val MIN_PANEL_WIDTH = 40

data class AppState(
    val splitterPosX: Int = 40,
    var dragging: Boolean = false,
    var dragStartMouseX: Int = 0,
    val dragStartSplitterPosX: Int = 40,
    val statusText: String = "",
    val statusText2: String = "",
    val mouseX: Int = 0,
    val width: Int = 200
){
    override fun toString():String {
        return """mouseX:${mouseX},sW:$splitterPosX,drg:$dragging,dSmX:$dragStartMouseX,dSsW:$dragStartSplitterPosX"""
    }
}

sealed interface Msg {
    data class StartDrag(val mouseX: Int) : Msg
    data class Drag(val mouseX: Int) : Msg
    data class EndDrag(val mouseX: Int) : Msg
    data class SetStatus(val text: String) : Msg
    data class SetStatus2(val text: String) : Msg
    data class SetMouseX(val mouseX: Int) : Msg
    object Quit : Msg
}
class App(private val ctx: AnsiVtDrawingContext) : VtEventListener {

    private var state = AppState()
    private var renderer: VtDomRenderer? = null
    private var running = true

    init {
        ctx.setEventListener(this)         // <-- use the generic setter
        ctx.onFrame = { frame() }
    }

    private fun dispatch(msg: Msg) {
        when (msg) {
            Msg.Quit -> {
                running = false
                ctx.stop()
            }
            else -> {
                state = update(state, msg)
            }
        }
    }

    override fun onEvent(ctx: VtDrawingContext, event: VtEvent) {
        // global shortcuts
        if (event is VtEvent.Key &&
            event.key.type == VtKeyType.Character &&
            event.key.ch == 'q') {
            dispatch(Msg.Quit)
            return
        }

        // let DOM event handlers run
        renderer?.onEvent(ctx, event)
    }
    fun appView(
        state: AppState,
        ctx: VtDrawingContext,
        dispatch: (Msg) -> Unit
    ): DomNode {
        val root = DomNode(id = "root", component = BoxComponent)
        val bg_top = VtColor.from(0x223388)
        val bg_panel = VtColor.from(0x636fab)
        val bg_splitter = VtColor.from(0x808080)
        val bg_editor = VtColor.from(0x182460)
        val bg_status = VtColor.from(0x4d4d4d)
        val fg = VtColor.from(0xbebebb)

        val w = ctx.windowWidth-4
        val h = ctx.windowHeight-4

        val titleLayout = DomLayout(
            left = 0,
            top = 0,
            right = w,
            bottom = 0            // height = 1
        ).apply {  }

        val mainLayout = DomLayout(
            left = 0,
            top = 1,
            right = w,
            bottom = h - 1        // height = h-2
        )

        val statusLayout = DomLayout(
            left = 0,
            top = h - 1,
            right = w,
            bottom = h - 1        // height = 1
        )

        // main horizontal layout from state
        val minPanelWidth = 20
        val maxPanelWidth = (w - 2).coerceAtLeast(minPanelWidth)
        val panelWidth = if(state.dragging) {
            // state.splitterPosX=state.mouseX
            state.mouseX
        } else {
            state.splitterPosX
        }.coerceIn(minPanelWidth, maxPanelWidth)

        val mainHeight = h - 2

        val leftLayout = DomLayout(
            left = 0,
            top = 0,
            right = panelWidth-1,
            bottom = mainHeight-1,
        )

        val splitterLayout = DomLayout(
            left = panelWidth,
            top = 0,
            right = panelWidth,           // width = 1
            bottom = mainHeight
        )

        val rightLayout = DomLayout(
            left = panelWidth + 1,
            top = 0,
            right = w,                // fill the remaining width inside main
            bottom = mainHeight
        )

        // components
        val titleBar = DomNode(
            id = "title",
            style = DomStyle(foreground = fg, background = bg_top),
            component = BoxComponent,
            text = " TUI Demo (q = quit) "
        ).apply {
            layout = titleLayout

            onMouseMove = { e ->
                dispatch(Msg.SetStatus("Title hovered ${e.globalX},${e.globalY}->${state}"))
            }
        }

        val main = DomNode(
            id = "main",
            style = DomStyle(foreground = fg, background = bg_editor),
            component = BoxComponent
        ).apply {
            layout = mainLayout

            onMouseMove = { e ->
                dispatch(Msg.SetStatus("main hovered ${e.globalX},${e.globalY}->${state}"))
                dispatch(Msg.SetMouseX(e.globalX))
            }
        }

        val statusBar = DomNode(
            id = "status",
            style = DomStyle(foreground = fg, background = bg_status),
            component = BoxComponent,
            text = "${state.statusText} | split=${panelWidth} drag=${state.dragging}"
        ).apply {
            layout = statusLayout

            onMouseMove = { e ->
                dispatch(Msg.SetStatus("status hovered ${e.globalX},${e.globalY}->${state}"))
            }
        }

        val statusBar2 = DomNode(
            id = "status2",
            style = DomStyle(foreground = fg, background = bg_status),
            component = BoxComponent,
            text = "${state.statusText} | split=${panelWidth} drag=${state.dragging}"
        ).apply {
            layout = statusLayout

            onMouseMove = { e ->
                dispatch(Msg.SetStatus("status hovered ${e.globalX},${e.globalY}->${state}"))
            }
        }

// Panels
        val fileTree = FileTreePanel(state,DomStyle(foreground = fg, background = bg_panel), leftLayout, dispatch)
        val splitter = Splitter(state,DomStyle(foreground = fg, background = bg_splitter), splitterLayout, dispatch)
        val editor = EditorPanel(state,DomStyle(foreground = fg, background = bg_editor), rightLayout, dispatch)

        root.addChild(titleBar)
        root.addChild(main.apply {
            addChild(fileTree)
            addChild(splitter)
            addChild(editor)
        })
        root.addChild(statusBar)

        return root
    }
    fun frame() {
        if (!running) return

        val root = appView(state, ctx, ::dispatch)

        // Recreate renderer each frame (simple; you can optimize later)
        renderer = VtDomRenderer(ctx, root)
        renderer!!.frame()
    }
}



fun update(state: AppState, msg: Msg): AppState =
    when (msg) {
        is Msg.StartDrag ->
            state.copy(
                dragging = true,
                dragStartMouseX = msg.mouseX,
                dragStartSplitterPosX = state.splitterPosX
            )

        is Msg.Drag ->
            if (!state.dragging) state
            else {
                val dx = msg.mouseX - state.dragStartMouseX
                state.copy(splitterPosX = state.dragStartSplitterPosX + dx)
            }

        is Msg.EndDrag -> {
            val dx = msg.mouseX - state.dragStartMouseX
            state.copy(
                splitterPosX = state.dragStartSplitterPosX + dx,
                dragging = false
            )
        }

        is Msg.SetStatus ->
            state.copy(statusText = msg.text)
        is Msg.SetStatus2 ->
            state.copy(statusText2 = msg.text)
        is Msg.SetMouseX -> {
            val minPanelWidth = 20
            val maxPanelWidth = (state.width - 2).coerceAtLeast(minPanelWidth)
            val panelWidth = if(state.dragging) {
                // state.splitterPosX=state.mouseX
                state.mouseX
            } else {
                state.splitterPosX
            }.coerceIn(minPanelWidth, maxPanelWidth)
            state.copy(mouseX = msg.mouseX, splitterPosX = panelWidth)
        }
        Msg.Quit ->
            state
    }
