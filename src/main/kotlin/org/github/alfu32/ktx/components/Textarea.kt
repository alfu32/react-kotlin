package org.github.alfu32.ktx.components

import org.github.alfu32.ktx.EditorViewport
import org.github.alfu32.ktx.ITextBuffer
import org.github.alfu32.ktx.handleKeyForBuffer
import org.github.alfu32.ktx.handleMouseToBuffer
import org.github.alfu32.ktx.renderBuffer
import react.Color
import react.ComponentTreeManager
import react.DOMNode
import react.StyleSet
import react.UIEventHandler
import react.renderComponent

fun Textarea(
    tree: ComponentTreeManager,
    buffer: ITextBuffer,
    style: StyleSet,
    onChange: (ITextBuffer) -> Unit = {},
    onMouseDown: UIEventHandler? = null,
    onMouseUp: UIEventHandler? = null,
    onMouseMove: UIEventHandler? = null,
    onKeyUp: UIEventHandler? = null,
    key: String? = null
): DOMNode = renderComponent(tree, key) {
    val left = style.left ?: 0
    val right = style.right ?: left
    val totalWidth = (right - left).coerceAtLeast(1)
    val top = style.top ?: 0
    val bottom = style.bottom ?: top
    val totalHeight = (bottom - top + 1).coerceAtLeast(7) // enforce min height

    val contentWidth = (totalWidth).coerceAtLeast(1)
    val viewportHeight = totalHeight

    val (scrollOffset, setScrollOffset) = useState { 0 }
    val totalLines = buffer.text().split('\n').size
    val maxOffset = (totalLines - viewportHeight).coerceAtLeast(0)
    val clampedOffset = scrollOffset.coerceIn(0, maxOffset)

    val rendered = renderBuffer(buffer, contentWidth, viewportHeight, clampedOffset)

    val slice = buffer.viewportSlice(
        EditorViewport(0, clampedOffset, contentWidth, viewportHeight),
        gutterWidth = 0
    )
    val contentNode = DOMNode(
        tag = "textarea-content",
        text = rendered,
        style = StyleSet.Companion.parse("left:0; top:0; right:${contentWidth}; bottom:${viewportHeight - 1}"),
        id = "textarea-content",
        onMouseDown = { ev ->
            // TODO
            // if(ev.button != null && (ev.button and 1) == 1) {
            handleMouseToBuffer(buffer, ev, singleLine = false, scrollOffset = clampedOffset, startSelection = true)
            onMouseDown?.invoke(ev)
            // }
            false
        },
        onMouseUp = onMouseUp,
        onMouseMove = { ev ->
            if (ev.button != null) {
                handleMouseToBuffer(
                    buffer,
                    ev,
                    singleLine = false,
                    scrollOffset = clampedOffset,
                    extendSelection = true
                )
                onMouseMove?.invoke(ev)
            }
        },
        onKeyDown = { ev ->
            if (handleKeyForBuffer(buffer, ev, singleLine = false)) onChange(buffer)
        },
        onKeyUp = onKeyUp,
        onMouseScroll = { ev ->
            val so = (scrollOffset + 3 * (ev.scrollDelta ?: 0)).coerceIn(-3, maxOffset + 5)
            setScrollOffset(so)
            false
        }
    )
    val scrollbar = VerticalScrollBar(
        tree = tree,
        style = StyleSet.Companion.parse("left:${contentWidth + 1}; top:0; right:${contentWidth + 1}; bottom:${viewportHeight - 1}"),
        contentHeight = totalLines.coerceAtLeast(viewportHeight),
        scrollOffset = clampedOffset,
        onScrollTo = { off -> setScrollOffset(off.coerceIn(-3, maxOffset + 5)) }
    )

    val cursorFg = style.bg ?: Color(0, 0, 0)
    val cursorBg = style.fg ?: Color(200, 200, 200)
    val cursorNode = slice.cursor?.let { c ->
        val cx = c.column
        val cy = c.line
        val ch = c.char.firstOrNull()?.let { if (it.isWhitespace()) '_' else it } ?: '_'
        DOMNode(
            tag = "editor-cursor",
            text = "$ch",
            style = StyleSet(left = cx, top = cy, right = cx, bottom = cy, fg = cursorFg, bg = cursorBg),
            id = "editor-cursor"
        )
    }
    val alternateCursorNode = DOMNode(
        tag = "editor-cursor",
        text = "_",
        style = StyleSet(left = 0, top = 0, right = 0, bottom = 0, fg = cursorFg, bg = cursorBg),
        id = "editor-cursor"
    )

    // Apply viewport offset by adjusting buffer? simplest: re-render buffer with slice starting at offset
    val slicedRendered = renderBuffer(buffer, contentWidth - 1, viewportHeight, clampedOffset)

    contentNode.copy(
        text = slicedRendered
    ).let { content ->
        DOMNode(
            tag = "textarea",
            text = null,
            style = style,
            id = "textarea",
            children = listOf(content, cursorNode ?: alternateCursorNode, scrollbar)
        )
    }
}