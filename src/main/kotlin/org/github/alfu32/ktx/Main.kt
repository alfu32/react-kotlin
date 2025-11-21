package org.github.alfu32.ktx

import org.github.alfu32.ktx.context.*

fun main() {
    val nodes = buildAppDom()
    val root = nodes.getValue("root")
    val title = nodes.getValue("title")
    val mainArea = nodes.getValue("main")
    val leftPanel = nodes.getValue("left-panel")
    val separator = nodes.getValue("separator")
    val content = nodes.getValue("content")
    val statusBar = nodes.getValue("status")

    val state = AppState()

    val dummyRendererCtx = VoidVtDrawingContext() // not actually used, renderer needs real ctx later
    val renderer = DomRenderer(dummyRendererCtx, root) // we'll rebind ctx below using the real one

    val ctx = AnsiVtDrawingContext(
        listener = object : VtEventListener {
            // placeholder, will be replaced by controller
            override fun onEvent(ctx: VtDrawingContext, event: VtEvent) {}
        },
        targetFps = 60
    )

    // Rebuild renderer with real context (simpler: just construct now that ctx exists)
    val realRenderer = DomRenderer(ctx, root)

    val controller = AppController(
        ctx = ctx,
        renderer = realRenderer,
        state = state,
        titleBar = title,
        mainArea = mainArea,
        leftPanel = leftPanel,
        separator = separator,
        contentArea = content,
        statusBar = statusBar
    )

    // Wire the event listener into ctx
    ctx.setEventListener(controller) // see helper below, or pass in constructor

    // Wire per-frame callback
    ctx.onFrame = { controller.frame() }

    // Optional: node-level callbacks if you want
    separator.onMouseDown = { /* additional behavior if desired */ }

    ctx.run()
}