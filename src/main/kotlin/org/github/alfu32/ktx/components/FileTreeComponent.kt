package org.github.alfu32.ktx.components

import org.github.alfu32.ktx.lib.FileTree
import org.github.alfu32.ktx.FileTreeEntry
import react.ComponentTreeManager
import react.DOMNode
import react.StyleSet
import react.UIEvent
import react.renderComponent

fun FileTreeComponent(
    tag:String ="",
    id:String ="",
    tree: ComponentTreeManager,
    rootDir: String,
    style: StyleSet,
    key: String? = null,
    selected: FileTreeEntry?=null,
    onFileSelected: (FileTreeEntry) -> Unit = {},
    onFolderSelected: (FileTreeEntry) -> Unit = {}
): DOMNode = renderComponent(tree, key) {
    val safeWidth = ((style.right ?: 0) - (style.left ?: 0)).coerceAtLeast(20)
    val safeHeight = ((style.bottom ?: 0) - (style.top ?: 0)).coerceAtLeast(10)
    val (fileTree, _) = useState { FileTree.Companion.newFileTree(rootDir) }
    val (version, setVersion) = useState { 0 } // version to trigger re-render
    val (scrollOffset, setScrollOffset) = useState { 0 }

    val viewportWidth = (safeWidth - 1).coerceAtLeast(1) // leave 1 column for scrollbar
    val contentWidth = (safeWidth - 1).coerceAtLeast(1)
    val viewportHeight = safeHeight

    val entries = fileTree.flattened()
    val maxOffset = (entries.size - viewportHeight).coerceAtLeast(0)
    val clampedScroll = scrollOffset.coerceIn(0, maxOffset)
    val lines = entries
        .drop(clampedScroll)
        .take(viewportHeight)
        .mapIndexed { idx, entry ->
            val indent = "  ".repeat(entry.padding)
            val prefix = when (entry.typ) {
                "folder" -> if (entry.isOpen) "[-] " else "[+] "
                else -> "    "
            }
            val label = (indent + prefix + entry.name).take(contentWidth)
            val text = label.padEnd(contentWidth - 1, ' ')
            val bg = if (entry.typ == "folder") "#4c548f;text-decoration:bold" else "#3c4678"
            val fg = if (entry.fullPath == selected?.fullPath) "#fd8d1d;text-decoration:bold" else "#e0e0e6"

            DOMNode(
                tag = "${tag}:entry",
                id = "${tag}:entry",
                key = entry.fullPath,
                text = text,
                style = StyleSet.Companion.parse("left:0;top:${idx};right:${contentWidth - 1};bottom:${idx};bg:$bg;fg:$fg"),
                onMouseDown = { event: UIEvent ->
                    if (entry.typ == "folder") {
                        val openerColumn = run {
                            val plusIdx = text.indexOf("[+]")
                            val minusIdx = text.indexOf("[-]")
                            when {
                                plusIdx >= 0 -> plusIdx
                                minusIdx >= 0 -> minusIdx
                                else -> -1
                            }
                        }
                        val toggleHit = openerColumn >= 0 &&
                                (event.relX ?: -1) in openerColumn..(openerColumn + 2)
                        if (toggleHit) {
                            fileTree.toggle(entry.fullPath)
                            fileTree.refreshOpenNodes()
                            setVersion(version + 1)
                        } else {
                            onFolderSelected(entry)
                        }
                    } else {
                        onFileSelected(entry)
                    }
                },
                onMouseScroll = { ev ->
                    val so = (scrollOffset + 3 * (ev.scrollDelta ?: 0)).coerceIn(-3, maxOffset + 5)
                    setScrollOffset(so)
                    false
                }
            )
        }

    val scrollbar = VerticalScrollBar(
        tree = tree,
        style = StyleSet.Companion.parse("left:${viewportWidth}; top:0; right:${viewportWidth}; bottom:${safeHeight - 1}"),
        contentHeight = entries.size.coerceAtLeast(viewportHeight),
        scrollOffset = clampedScroll,
        onScrollTo = { off -> setScrollOffset(off.coerceIn(0, maxOffset)) }
    )

    DOMNode(
        tag = tag,
        style = StyleSet.Companion.parse("left:0;top:0;right:${viewportWidth};bottom:${safeHeight - 1}"),
        id = tag,
        children = lines + scrollbar,
    )
}