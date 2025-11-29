package org.github.alfu32.ktx.components

import react.DOMNode
import react.StyleSet
import react.UIEventHandler

// Simple vertical splitter component: renders a vertical bar filling its styled height.
fun VerticalSplitter(
    style: StyleSet,
    onMouseDown: UIEventHandler? = null,
    onMouseMove: UIEventHandler? = null,
    onMouseUp: UIEventHandler? = null
): DOMNode {
    val top = style.top ?: 0
    val bottom = style.bottom ?: top
    val height = (bottom - top + 1).coerceAtLeast(1)
    val bar = buildString {
        repeat(height) { idx ->
            append('│')
            if (idx != height - 1) append('\n')
        }
    }
    return DOMNode(
        tag = "vertical-splitter",
        text = bar,
        style = style,
        id = "vertical-splitter",
        onMouseDown = onMouseDown,
        onMouseUp = onMouseUp,
        onMouseMove = onMouseMove,
    )
}

// Simple vertical splitter component: renders a vertical bar filling its styled height.
fun VerticalSplitter(
    style: StyleSet,
    onMouseDown: UIEventHandler? = null,
    onMouseMove: UIEventHandler? = null,
    onMouseUp: UIEventHandler? = null,
    key: String? = null
): DOMNode {
    val top = style.top ?: 0
    val bottom = style.bottom ?: top
    val height = (bottom - top + 1).coerceAtLeast(1)
    val bar = buildString {
        repeat(height) { idx ->
            append('│')
            if (idx != height - 1) append('\n')
        }
    }
    return DOMNode(
        tag = "vertical-splitter",
        text = bar,
        style = style,
        id = "vertical-splitter",
        onMouseDown = onMouseDown,
        onMouseUp = onMouseUp,
        onMouseMove = onMouseMove,
    )
}