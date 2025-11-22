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
    val mouseX: Int = 40,
){
    override fun toString():String {
        return """sW:$splitterPosX,drg:$dragging,dSmX:$dragStartMouseX,dSsW:$dragStartSplitterPosX"""
    }
}

class App(private val ctx: AnsiVtDrawingContext) : VtEventListener {

    private var state = AppState()
    private var renderer: VtDomRenderer? = null
    private var running = true

    init {
        ctx.setEventListener(this)         // <-- use the generic setter
        ctx.onFrame = { frame() }
    }

    private fun dispatch(json: String) {
        val payload = parseJson(json)
        when (payload["type"]) {
            "quit" -> {
                running = false
                ctx.stop()
            }
            "start_drag" -> {
                val mouseX = payload["mouseX"]?.toIntOrNull() ?: return
                state = state.copy(
                    dragging = true,
                    dragStartMouseX = mouseX,
                    dragStartSplitterPosX = state.splitterPosX
                )
            }
            "drag" -> {
                val mouseX = payload["mouseX"]?.toIntOrNull() ?: return
                if (!state.dragging) return
                val dx = mouseX - state.dragStartMouseX
                state = state.copy(splitterPosX = state.dragStartSplitterPosX + dx)
            }
            "end_drag" -> {
                val mouseX = payload["mouseX"]?.toIntOrNull() ?: return
                val dx = mouseX - state.dragStartMouseX
                state = state.copy(
                    splitterPosX = state.dragStartSplitterPosX + dx,
                    dragging = false
                )
            }
            "status" -> {
                val text = payload["text"] ?: ""
                state = state.copy(statusText = text)
            }
            "mouseX" -> {
                val text = payload["mouseX"] ?: ""
                state = state.copy(statusText = text, mouseX = text.toInt())
            }
        }
    }

    private fun parseJson(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val regex = Regex("\"([^\"]+)\"\\s*:\\s*(\"([^\"]*)\"|[-\\d]+|true|false)")
        regex.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            val raw = match.groupValues[2]
            val value = if (raw.startsWith("\"")) raw.trim('"') else raw
            map[key] = value
        }
        return map
    }

    override fun onEvent(ctx: VtDrawingContext, event: VtEvent) {
        // global shortcuts
        if (event is VtEvent.Key &&
            event.key.type == VtKeyType.Character &&
            event.key.ch == 'q') {
            dispatch("""{"type":"quit"}""")
            return
        }

        // let DOM event handlers run
        renderer?.onEvent(ctx, event)
    }
    fun appView(
        state: AppState,
        ctx: VtDrawingContext,
        dispatch: (String) -> Unit
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
        val minPanelWidth = MIN_PANEL_WIDTH
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
                dispatch("""{"type":"status","text":"Title hovered ${'$'}{e.globalX},${'$'}{e.globalY}->${state}"}""")
            }
        }

        val main = DomNode(
            id = "main",
            style = DomStyle(foreground = fg, background = bg_editor),
            component = BoxComponent
        ).apply {
            layout = mainLayout

            onMouseMove = { e ->
                dispatch("""{"type":"status","text":"main hovered ${'$'}{e.globalX},${'$'}{e.globalY}->${state}"}""")
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
                dispatch("""{"type":"status","text":"status hovered ${'$'}{e.globalX},${'$'}{e.globalY}->${state}"}""")
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
