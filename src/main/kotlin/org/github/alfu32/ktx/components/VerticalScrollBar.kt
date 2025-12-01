package org.github.alfu32.ktx.components

import react.ComponentTreeManager
import react.DOMNode
import react.StyleSet
import react.renderComponent
import kotlin.math.abs

fun VerticalScrollBar(
    parent: String,
    tree: ComponentTreeManager,
    style: StyleSet,
    contentHeight: Int,
    scrollOffset: Int,
    onScrollTo: (Int) -> Unit,
    onScrolling: (Boolean) -> Unit,
    key: String? = null
): DOMNode = renderComponent(tree, key) {
    val wrapper = DOMNode(
            tag = "vertical-scrollbar",
            style = style,
            id = "vertical-scrollbar",
        )
    val barHeight = abs((style.bottom ?: 0) - (style.top ?: 0) + 1).coerceAtLeast(5)
    val barWidth = 1 //abs((style.right ?: 0) - (style.left ?: 0) + 1).coerceAtLeast(0)
    val ch = contentHeight.coerceAtLeast(barHeight)
    val maxOffset = (ch - barHeight).coerceAtLeast(0)
    val clampedOffset = scrollOffset.coerceIn(0, maxOffset)

    val indicatorHeight = ((barHeight * barHeight) / ch).coerceAtLeast(1)
    val trackRoom = (barHeight - indicatorHeight).coerceAtLeast(0)
    val indicatorTop = if (maxOffset == 0 || trackRoom == 0) 0 else (clampedOffset * trackRoom) / maxOffset

    val (dragging, setDragging) = useState { false }
    val (dragStartY, setDragStartY) = useState { 0 }
    val (dragStartTop, setDragStartTop) = useState { indicatorTop }
    onScrolling(dragging)

    val left = if(dragging) -4 else 0
    val right = if(dragging) +2 else 1

    fun toOffset(indicatorPos: Int): Int {
        val pos = indicatorPos.coerceIn(0, trackRoom)
        return if (trackRoom == 0 || maxOffset == 0) 0 else (pos * maxOffset) / trackRoom
    }

    // Wider visuals: two columns for track/indicator to make it easier to grab
    val indicatorStyle = StyleSet.Companion.parse(
        "left:${left}; top:${indicatorTop}; right:${right}; bottom:${indicatorTop + indicatorHeight - 1}"
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

    val overlay = DOMNode(
        id = "$parent>scrollbar-overlay",
        tag = "invisible",
        style = StyleSet.Companion.parse(
            "z-index:99999;left:${left}; top:0; right:${right}; bottom:${barHeight - 1}"
        ),
        onMouseMove = { ev ->
            if (!dragging) return@DOMNode
            val y = ev.relY ?: 0
            val dy = y - dragStartY
            val newTop = dragStartTop + dy
            onScrollTo(toOffset(newTop))
            // ev.stopPropagation()
        },
        onMouseUp = { ev -> setDragging(false) },
        visible = dragging
    )

    val track = DOMNode(
        tag = "scrollbar-track",
        style = StyleSet.Companion.parse("left:${left}; top:0; right:${right}; bottom:${barHeight - 1}"),
        id = "scrollbar-track",
        onMouseDown = { ev ->
            val y = ev.relY ?: 0
            val targetTop = (y - indicatorHeight / 2).coerceIn(0, trackRoom)
            onScrollTo(toOffset(targetTop))
        },
        children = listOf( overlay,indicator,),
    )

    wrapper.children = listOf(track)
    return wrapper
    // track
}
