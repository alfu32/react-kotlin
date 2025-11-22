package org.github.alfu32.ktx

import org.github.alfu32.ktx.BoxComponent
import org.github.alfu32.ktx.vdom.DomLayout
import org.github.alfu32.ktx.vdom.DomNode
import org.github.alfu32.ktx.vdom.DomStyle
import kotlin.text.repeat

fun Splitter(
    state: AppState,
    style: DomStyle,
    layout: DomLayout,
    dispatch: (Msg) -> Unit
): DomNode {
    return DomNode(
        id = "splitter",
        style=style,
        component = BoxComponent,
        text = "│\n".repeat((layout.bottom - layout.top + 1).coerceAtLeast(1))
    ).apply {
        this.layout = layout

        onMouseDown = { e ->
            dispatch(Msg.StartDrag(e.globalX))
            dispatch(Msg.SetStatus("Splitter click ${e.globalX},${e.globalY}->${state}"))
        }
        onMouseMove = { e ->
            dispatch(Msg.Drag(e.globalX))
            dispatch(Msg.SetStatus("Splitter dragging ${e.globalX},${e.globalY}->${state}"))
        }
        onMouseUp = { e ->
            dispatch(Msg.EndDrag(e.globalX))
            dispatch(Msg.SetStatus("Splitter finished ${e.globalX},${e.globalY}->${state}"))
        }
    }
}
