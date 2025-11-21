package org.github.alfu32.ktx

import org.github.alfu32.ktx.color.*
import org.github.alfu32.ktx.context.*
import org.github.alfu32.ktx.vdom.*

fun buildAppDom(): Map<String, DomNode> {
    val root = DomNode(id = "root")

    val titleBar = root.addChild(
        DomNode(
            id = "title",
            text = "  My TUI App  (drag separator, press 'q' to quit)",
            style = DomStyle(
                foreground = VtColor(255.toByte(), 255.toByte(), 255.toByte()),
                background = VtColor(0.toByte(), 0.toByte(), 160.toByte()),
                bold = true
            )
        )
    )

    val mainArea = root.addChild(
        DomNode(
            id = "main",
            style = DomStyle(
                foreground = VtColor(220.toByte(), 220.toByte(), 220.toByte()),
                background = VtColor(0.toByte(), 0.toByte(), 0.toByte())
            )
        )
    )

    val leftPanel = mainArea.addChild(
        DomNode(
            id = "left-panel",
            text = "Left panel",
            style = DomStyle(
                foreground = VtColor(255.toByte(), 255.toByte(), 255.toByte()),
                background = VtColor(0.toByte(), 64.toByte(), 64.toByte())
            )
        )
    )

    val separator = mainArea.addChild(
        DomNode(
            id = "separator",
            text = "│",  // vertical bar; will be clipped to column
            style = DomStyle(
                foreground = VtColor(255.toByte(), 255.toByte(), 0.toByte()),
                background = VtColor(0.toByte(), 0.toByte(), 0.toByte()),
                bold = true
            )
        )
    )

    val contentArea = mainArea.addChild(
        DomNode(
            id = "content",
            text = "Main content area",
            style = DomStyle(
                foreground = VtColor(255.toByte(), 255.toByte(), 255.toByte()),
                background = VtColor(16.toByte(), 16.toByte(), 16.toByte())
            )
        )
    )

    val statusBar = root.addChild(
        DomNode(
            id = "status",
            text = "",
            style = DomStyle(
                foreground = VtColor(0.toByte(), 0.toByte(), 0.toByte()),
                background = VtColor(192.toByte(), 192.toByte(), 192.toByte())
            )
        )
    )

    return mapOf(
        "root" to root,
        "title" to titleBar,
        "main" to mainArea,
        "left-panel" to leftPanel,
        "separator" to separator,
        "content" to contentArea,
        "status" to statusBar
    )
}

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