package org.github.alfu32.ktx.components

import org.github.alfu32.ktx.EditorState
import org.github.alfu32.ktx.EditorViewport
import org.github.alfu32.ktx.ITextBuffer
import org.github.alfu32.ktx.handleKeyForBuffer
import org.github.alfu32.ktx.handleMouseToBuffer
import react.Color
import react.ComponentTreeManager
import react.DOMNode
import react.StyleSet
import react.UIEvent
import react.renderComponent

fun CodeEditor(
    tree: ComponentTreeManager,
    buffer: ITextBuffer,
    style: StyleSet,
    filePath: String,
    language: String,
    onChange: (ITextBuffer) -> Unit = {},
    onStateChange: (EditorState) -> Unit = {},
    key: String? = null
): DOMNode = renderComponent(tree, key) {
    val totalWidth = ((style.right ?: 0) - (style.left ?: 0) + 1).coerceAtLeast(10)
    val totalHeight = ((style.bottom ?: 0) - (style.top ?: 0) + 1).coerceAtLeast(10)
    val scrollbarWidth = 1
    val totalLines = buffer.text().split('\n').size.coerceAtLeast(1)
    val gutterWidth = (totalLines.toString().length + 2).coerceAtLeast(3)
    val contentWidth = (totalWidth - scrollbarWidth - gutterWidth).coerceAtLeast(1)
    val viewportHeight = totalHeight

    val (scrollOffset, setScrollOffset) = useState { 0 }
    val maxOffset = (totalLines - viewportHeight).coerceAtLeast(0)
    val clampedOffset = scrollOffset.coerceIn(0, maxOffset)

    val slice = buffer.viewportSlice(
        EditorViewport(0, clampedOffset, contentWidth, viewportHeight),
        gutterWidth = gutterWidth - 1
    )
    val rendered = slice.lines.joinToString("\n") { line ->
        buildString {
            line.segments.forEach { append(it.text) }
        }
    }
    val gutter = DOMNode(
        tag = "editor-gutter",
        id = "editor-gutter",
        text = slice.lines.joinToString("\n") { line -> line.gutter },
        style = StyleSet(
            left = 0,
            top = 0,
            right = gutterWidth,
            bottom = viewportHeight,
            fg = style.fg ?: Color(220, 220, 100),
            bg = style.bg ?: Color(34, 60, 97)
        )
        // StyleSet.parse("left:0;top:0,bottom:$viewportHeight;right:$gutterWidth;bg:#787878;fg:#199100100;text-decoration:bold"),
    )

    // Selection overlays (background only)
    val selectionNodes = mutableListOf<DOMNode>()
    slice.lines.forEachIndexed { idx, line ->
        var x = 0
        line.segments.forEach { seg ->
            val len = seg.text.length
            if (seg.selected && len > 0) {
                val left = gutterWidth + x  // align with caret offset
                val right = gutterWidth + x + len - 1
                selectionNodes.add(
                    DOMNode(
                        tag = "editor-selection",
                        text = seg.text,
                        style = StyleSet(
                            left = left,
                            top = idx,
                            right = right,
                            bottom = idx,
                            fg = style.fg ?: Color(220, 220, 100),
                            bg = style.bg ?: Color(34, 60, 97)
                        )
                    )
                )
            }
            x += len
        }
    }

    val contentNode = DOMNode(
        tag = "editor-content",
        text = rendered,
        style = StyleSet.Companion.parse("left:$gutterWidth; top:0; right:${gutterWidth + contentWidth - 1}; bottom:${viewportHeight - 1}"),
        id = "editor-content",
        onKeyDown = { ev ->
            /// TODO
            /// if(ev.button != null && ev.button == 1) {
            if (handleKeyForBuffer(buffer, ev, singleLine = false)) onChange(buffer)
            /// }
            false
        },
        onMouseDown = { ev ->
            val adj = ev.alterCopy(UIEvent(kind = ev.kind, relX = (ev.relX ?: 0) - gutterWidth + 1, relY = ev.relY))
            handleMouseToBuffer(buffer, adj, singleLine = false, scrollOffset = clampedOffset, startSelection = true)
        },
        onMouseMove = { ev ->
            if (ev.button != null) {
                val adj = ev.alterCopy(UIEvent(kind = ev.kind, relX = (ev.relX ?: 0) - gutterWidth, relY = ev.relY))
                handleMouseToBuffer(
                    buffer,
                    adj,
                    singleLine = false,
                    scrollOffset = clampedOffset,
                    extendSelection = true
                )
            }
        },
        onMouseScroll = { ev ->
            val so = (scrollOffset + 3 * (ev.scrollDelta ?: 0)).coerceIn(-3, maxOffset + 5)
            setScrollOffset(so)
            false
        }
    )

    val cursorNode = slice.cursor?.let { c ->
        val cx = gutterWidth + c.column
        val cy = c.line
        val ch = c.char.firstOrNull()?.let { if (it.isWhitespace()) '_' else it } ?: '_'
        val fg = style.bg ?: Color(0, 0, 0)
        val bg = style.fg ?: Color(200, 200, 200)
        DOMNode(
            tag = "editor-cursor",
            text = "$ch",
            style = StyleSet(left = cx, top = cy, right = cx, bottom = cy, fg = fg, bg = bg),
            id = "editor-cursor"
        )
    }

    val scrollbar = VerticalScrollBar(
        parent="editor",
        tree = tree,
        style = StyleSet.Companion.parse("left:${gutterWidth + contentWidth}; top:0; right:${gutterWidth + contentWidth}; bottom:${viewportHeight - 1}"),
        contentHeight = totalLines.coerceAtLeast(viewportHeight),
        scrollOffset = clampedOffset,
        onScrollTo = { off -> setScrollOffset(off.coerceIn(0, maxOffset)) },
        onScrolling = { state ->  },
    )

    val children = listOfNotNull(gutter, contentNode, cursorNode) + selectionNodes + listOf(scrollbar)

    onStateChange(
        EditorState(
            filePath = filePath,
            language = language,
            cursorLine = buffer.cursorPosition().line,
            cursorColumn = buffer.cursorPosition().column,
            selection = buffer.selectionText(),
            totalLines = buffer.totalLines(),
            bom = buffer.bom(),
            encoding = buffer.encoding()
        )
    )

    DOMNode(
        tag = "editor",
        style = style,
        id = "editor",
        children = children
    )
}