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
    dispatch: (String) -> Unit
): DomNode {
    return DomNode(
        id = "splitter",
        style=style,
        component = BoxComponent,
        text = "│\n".repeat((layout.bottom - layout.top + 1).coerceAtLeast(1))
    ).apply {
        this.layout = layout

        onMouseDown = { e ->
            dispatch("""{"type":"start_drag","mouseX":${e.globalX}}""")
            dispatch("""{"type":"status","text":"Splitter click ${e.globalX},${e.globalY}->${state}"}""")
        }
        onMouseMove = { e ->
            dispatch("""{"type":"drag","mouseX":${e.globalX}}""")
            dispatch("""{"type":"status","text":"Splitter dragging ${e.globalX},${e.globalY}->${state}"}""")
        }
        onMouseUp = { e ->
            dispatch("""{"type":"end_drag","mouseX":${e.globalX}}""")
            dispatch("""{"type":"status","text":"Splitter finished ${e.globalX},${e.globalY}->${state}"}""")
        }
    }
}
