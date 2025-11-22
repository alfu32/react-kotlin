package org.github.alfu32.ktx

import org.github.alfu32.ktx.context.*

fun main() {
    val ctx = AnsiVtDrawingContext(
        listener = VtEventListener { _, _ -> }, // placeholder; App will set itself
        targetFps = 60
    )

    val app = App(ctx)

    ctx.run()
}