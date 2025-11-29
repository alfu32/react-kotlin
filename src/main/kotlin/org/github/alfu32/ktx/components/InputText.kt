package org.github.alfu32.ktx.components

import org.github.alfu32.ktx.ITextBuffer
import org.github.alfu32.ktx.handleKeyForBuffer
import org.github.alfu32.ktx.handleMouseToBuffer
import org.github.alfu32.ktx.renderBuffer
import react.DOMNode
import react.StyleSet
import react.UIEventHandler

fun InputText(
    buffer: ITextBuffer,
    style: StyleSet,
    onChange: (ITextBuffer) -> Unit = {},
    onMouseDown: UIEventHandler? = null,
    onMouseUp: UIEventHandler? = null,
    onMouseMove: UIEventHandler? = null,
    onKeyUp: UIEventHandler? = null,
    key: String? = null
): DOMNode {
    val left = style.left ?: 0
    val right = style.right ?: left
    val width = (right - left + 1).coerceAtLeast(1)
    val rendered = renderBuffer(buffer, width, 1)
    return DOMNode(
        tag = "input-text",
        text = rendered,
        style = style,
        id = "input-text",
        onMouseDown = { ev ->
            handleMouseToBuffer(buffer, ev, singleLine = true)
            onMouseDown?.invoke(ev)
        },
        onMouseUp = onMouseUp,
        onMouseMove = { ev ->
            handleMouseToBuffer(buffer, ev, singleLine = true)
            onMouseMove?.invoke(ev)
        },
        onKeyDown = { ev ->
            if (handleKeyForBuffer(buffer, ev, singleLine = true)) onChange(buffer)
        },
        onKeyUp = onKeyUp,
        key = key
    )
}