package org.github.alfu32.ktx

import org.github.alfu32.ktx.color.VtColor
import org.github.alfu32.ktx.context.AnsiVtDrawingContext
import org.github.alfu32.ktx.context.VtEvent
import org.github.alfu32.ktx.context.VtEventListener
import org.github.alfu32.ktx.context.VtKeyType
import org.github.alfu32.ktx.vdom.DomLayout
import org.github.alfu32.ktx.vdom.DomNode
import org.github.alfu32.ktx.vdom.DomStyle

fun main1() {
    val ctx = AnsiVtDrawingContext(listener = VtEventListener { ctx, event ->
        when (event) {
            is VtEvent.Resize -> {
                ctx.clear()
                ctx.drawText(0, 0, "Size: ${event.width}x${event.height}")
            }
            is VtEvent.Key -> {
                if (event.key.type == VtKeyType.Character && event.key.ch == 'q') {
                    (ctx as AnsiVtDrawingContext).stop()
                }
            }
            is VtEvent.Mouse -> {
                ctx.drawText(0, 1, "Mouse: (${event.x}, ${event.y}) ${event.kind}      ")
            }
        }
    })
    ctx.run()
}

fun main() {
    val root = DomNode(
        text = "Hello DOM",
        layout = DomLayout(left = 1, top = 1, right = 1, bottom = 1),
        style = DomStyle(
            foreground = VtColor(255.toByte(), 255.toByte(), 255.toByte()),
            background = VtColor(0.toByte(), 0.toByte(), 128.toByte()),
            bold = true
        )
    )

    lateinit var renderer: DomRenderer

    val ctx = AnsiVtDrawingContext(
        listener = VtEventListener { context, event ->
            renderer.onEvent(context, event)
        },
        targetFps = 60
    )

    renderer = DomRenderer(ctx, root)

    root.onKey = { e ->
        if (e.key.type == VtKeyType.Character && e.key.ch == 'q') {
            ctx.stop()
        }
    }

    ctx.onFrame = { renderer.frame() }

    ctx.run()
}