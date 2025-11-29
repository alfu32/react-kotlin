package org.github.alfu32.ktx.components

import react.ComponentTreeManager
import react.DOMNode
import react.StyleSet
import react.renderComponent

fun VerticalScrollBar(
    tree: ComponentTreeManager,
    style: StyleSet,
    contentHeight: Int,
    scrollOffset: Int,
    onScrollTo: (Int) -> Unit,
    key: String? = null
): DOMNode = renderComponent(tree, key) {
    val vh = ((style.bottom ?: 0) - (style.top ?: 0) + 1).coerceAtLeast(5)
    val vw = ((style.right ?: 0) - (style.left ?: 0)).coerceAtLeast(0)
    val ch = contentHeight.coerceAtLeast(vh)
    val maxOffset = (ch - vh).coerceAtLeast(0)
    val clampedOffset = scrollOffset.coerceIn(0, maxOffset)

    val indicatorHeight = ((vh * vh) / ch).coerceAtLeast(1)
    val trackRoom = (vh - indicatorHeight).coerceAtLeast(0)
    val indicatorTop = if (maxOffset == 0 || trackRoom == 0) 0 else (clampedOffset * trackRoom) / maxOffset

    val (dragging, setDragging) = useState { false }
    val (dragStartY, setDragStartY) = useState { 0 }
    val (dragStartTop, setDragStartTop) = useState { indicatorTop }

    fun toOffset(indicatorPos: Int): Int {
        val pos = indicatorPos.coerceIn(0, trackRoom)
        return if (trackRoom == 0 || maxOffset == 0) 0 else (pos * maxOffset) / trackRoom
    }

    // Wider visuals: two columns for track/indicator to make it easier to grab
    val indicatorStyle = StyleSet.Companion.parse(
        "left:0; top:${indicatorTop}; right:${vw}; bottom:${indicatorTop + indicatorHeight - 1}"
    )

    val indicator = DOMNode(
        id = "scrollbar-indicator",
        tag = "scrollbar-indicator",
        style = indicatorStyle,
        onMouseDown = { ev ->
            val y = ev.relY ?: 0
            setDragging(true)
            setDragStartY(y)
            setDragStartTop(indicatorTop)
        },
    )

    val hitArea = DOMNode(
        id = "hit-area",
        tag = "invisible",
        style = StyleSet.Companion.parse(
            "left:${if (dragging) -230 else 0}; top:${if (dragging) -230 else indicatorTop}; right:${if (dragging) 230 else vw}; bottom:${if (dragging) 150 else indicatorTop + indicatorHeight - 1}"
        ),
        onMouseMove = { ev ->
            if (!dragging) return@DOMNode
            val y = ev.relY ?: 0
            val dy = y - dragStartY
            val newTop = dragStartTop + dy
            onScrollTo(toOffset(newTop))
        },
        onMouseUp = { ev -> setDragging(false) },
        visible = false
    )

    val track = DOMNode(
        tag = "scrollbar-track",
        style = StyleSet.Companion.parse("left:0; top:0; right:${vw}; bottom:${vh - 1}"),
        id = "scrollbar-track",
        onMouseDown = { ev ->
            val y = ev.relY ?: 0
            val targetTop = (y - indicatorHeight / 2).coerceIn(0, trackRoom)
            onScrollTo(toOffset(targetTop))
        },
        children = listOf(hitArea, indicator),
    )

    DOMNode(
        tag = "vertical-scrollbar",
        style = style,
        id = "vertical-scrollbar",
        children = listOf(track),
    )
    // track
}