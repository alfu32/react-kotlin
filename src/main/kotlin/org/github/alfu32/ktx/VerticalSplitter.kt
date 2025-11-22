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
        text = "│\n".repeat(layout.bottom - layout.top)
    ).apply {
        this.layout = layout

        onMouseDown = { e ->
            dispatch(Msg.StartDrag(e.globalX))
        }
        onMouseMove = { e ->
            if (state.dragging) dispatch(Msg.Drag(e.globalX))
        }
        onMouseUp = { _ ->
            if (state.dragging) dispatch(Msg.EndDrag)
        }
    }
}