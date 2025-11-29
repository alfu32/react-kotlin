package org.github.alfu32.ktx.components

import react.DOMNode
import react.StyleSet
import react.UIEventHandler

// Horizontal splitter component (single-row line).
fun HorizontalSplitter(
    style: StyleSet,
    onMouseDown: UIEventHandler? = null,
    onMouseMove: UIEventHandler? = null,
    onMouseUp: UIEventHandler? = null,
    key: String? = null
): DOMNode {
    val left = style.left ?: 0
    val right = style.right ?: left
    val width = (right - left + 1).coerceAtLeast(1)
    val line = "─".repeat(width)
    return DOMNode(
        tag = "horizontal-splitter",
        text = line,
        style = style,
        id = "horizontal-splitter",
        onMouseDown = onMouseDown,
        onMouseUp = onMouseUp,
        onMouseMove = onMouseMove,
    )
}