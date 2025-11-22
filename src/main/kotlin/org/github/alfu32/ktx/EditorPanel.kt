package org.github.alfu32.ktx

import org.github.alfu32.ktx.BoxComponent
import org.github.alfu32.ktx.vdom.DomLayout
import org.github.alfu32.ktx.vdom.DomNode
import org.github.alfu32.ktx.vdom.DomStyle

fun EditorPanel(
    state: AppState,
    style: DomStyle,
    layout: DomLayout,
    dispatch: (String) -> Unit
): DomNode {
    return DomNode(
        id = "editor",
        style=style,
        component = BoxComponent,
        text = "Editor\n[placeholder]"
    ).apply {
        this.layout = layout

        onMouseDown = { e ->
            dispatch("""{"type":"status","text":"Editor click ${e.globalX},${e.globalY}->${state}"}""")
        }
        onMouseMove = { e ->
            dispatch("""{"type":"status","text":"Editor hovered ${e.globalX},${e.globalY}->${state}"}""")
        }
    }
}
