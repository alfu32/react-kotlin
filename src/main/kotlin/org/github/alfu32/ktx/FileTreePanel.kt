package org.github.alfu32.ktx

import org.github.alfu32.ktx.BoxComponent
import org.github.alfu32.ktx.vdom.DomLayout
import org.github.alfu32.ktx.vdom.DomNode
import org.github.alfu32.ktx.vdom.DomStyle

fun FileTreePanel(
    state: AppState,
    style: DomStyle,
    layout: DomLayout,
    dispatch: (Msg) -> Unit
): DomNode {
    return DomNode(
        id = "file-tree",
        style=style,
        component = BoxComponent,
        text = "File tree\n[placeholder]"
    ).apply {
        this.layout = layout

        onMouseDown = { e ->
            dispatch(Msg.SetStatus("FileTree click ${e.globalX},${e.globalY}->${state}"))
        }
        onMouseMove = { e ->
            dispatch(Msg.SetStatus("FileTree hovered ${e.globalX},${e.globalY}->${state}"))
        }
    }
}