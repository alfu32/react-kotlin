package org.github.alfu32.ktx.components

import react.DOMNode
import react.StyleSet
import react.UIEventHandler

fun Button(
    text: String,
    onClick: UIEventHandler? = null,
    style: StyleSet = StyleSet(),
    key: String? = null
): DOMNode {
    val spaces = " ".repeat(text.length+2)
    return DOMNode(
        tag = "button",
        text = "$spaces\n $text \n$spaces",
        style = style,
        id = "button",
        onMouseDown = onClick,
    )
}