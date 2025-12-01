package org.github.alfu32.ktx.components

import org.github.alfu32.ktx.lib.GitService
import org.github.alfu32.ktx.lib.JGitService
import org.github.alfu32.ktx.lib.TextBuffer
import react.ComponentTreeManager
import react.DOMNode
import react.StyleSet
import react.renderComponent
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/* =====================================================================
   GIT PANEL COMPONENT (read-only actions + layout scaffolding)
   ===================================================================== */
fun GitComponent(
    tree: ComponentTreeManager,
    workspaceRoot: String,
    style: StyleSet,
    key: String? = null,
    gitFactory: (File) -> GitService = { root -> JGitService(root) },
    onCommit: (String) -> Unit = {},
    onTag: (String) -> Unit = {},
    onPush: () -> Unit = {}
): DOMNode = renderComponent(tree, key) {
    val safeWidth = ((style.right ?: 0) - (style.left ?: 0) + 1).coerceAtLeast(20)
    val safeHeight = ((style.bottom ?: 0) - (style.top ?: 0) + 1).coerceAtLeast(12)
    val contentWidth = safeWidth - 4
    val (gitService, _) = useState { gitFactory(File(workspaceRoot)) }
    val statusEntries = gitService.statusPorcelain()
    val commits = gitService.listCommits(40)
    val branch = gitService.currentBranch()
    val (message, setMessage) = useState { "" }
    val (commitScroll, setCommitScroll) = useState { 0 }
    val statusText = if (statusEntries.isEmpty()) "(clean)"
    else statusEntries.joinToString("\n") {
        "${it.code.padEnd(2)} ${
            (it.path + " ".repeat(contentWidth)).take(
                contentWidth
            )
        }"
    }

    val commitLineWidth = contentWidth.coerceAtLeast(10)
    val commitLines = commits.map { c ->
        val hashShort = c.hash.take(8).padEnd(8, ' ')
        val dateStr = c.date?.let { commitDateFormatter.format(it) } ?: "----"
        val author = c.author.take(12).padEnd(12, ' ')
        val msg = c.message.lines().firstOrNull() ?: ""
        "$hashShort  $dateStr  $author  $msg".take(contentWidth)
    }.map { line -> line.take(commitLineWidth) }

    val header = DOMNode(
        tag = "git-header",
        text = "origin/$branch",
        style = StyleSet.Companion.parse("left:0; top:0; right:${contentWidth}; bottom:0"),
        id = "git-header",
    )

    val statusBoxHeight = (safeHeight / 3).coerceAtLeast(5)
    val statusBox = DOMNode(
        tag = "git-status",
        text = statusText.take(contentWidth),
        style = StyleSet.Companion.parse("left:0; top:1; right:${contentWidth}; bottom:${statusBoxHeight}"),
        id = "git-status",
    )

    val splitter1 = HorizontalSplitter(
        style = StyleSet.Companion.parse("left:0; top:${statusBoxHeight + 1}; right:${contentWidth}; bottom:${statusBoxHeight + 1}")
    )

    val messageBoxTop = statusBoxHeight + 2
    val messageBoxHeight = 10
    // val messageLabel = DOMNode(
    //     tag = "git-message-label",
    //     text = "${workspaceRoot}\nMessage",
    //     style = StyleSet.parse("left:0; top:${messageBoxTop}; right:${safeWidth - 1}; bottom:${messageBoxTop}"),
    //     id = "git-message-label",
    // )

    val (messageBuf, _) = useState { TextBuffer().apply { loadText(message) } }
    val messageArea = Textarea(
        parent="git-panel",
        tree = tree,
        buffer = messageBuf,
        style = StyleSet.Companion.parse("left:0; top:${messageBoxTop}; right:${contentWidth}; bottom:${messageBoxTop + messageBoxHeight}"),
        onChange = { buf -> setMessage(buf.text()) }
    )

    val buttonsTop = messageBoxTop + messageBoxHeight + 1
    val buttonWidth = (contentWidth / 3).coerceAtLeast(8)
    val commitBtn = Button(
        text = " commit ",
        style = StyleSet.Companion.parse("left:1; top:${buttonsTop}; right:${8}; bottom:${buttonsTop}"),
        onClick = {
            onCommit(message)
        }
    )
    val tagBtn = Button(
        text = "  tag   ",
        style = StyleSet.Companion.parse("left:${10}; top:${buttonsTop}; right:${17}; bottom:${buttonsTop}"),
        onClick = {
            onTag(message)
        }
    )
    val pushBtn = Button(
        text = "  push  ",
        style = StyleSet.Companion.parse("left:${19}; top:${buttonsTop}; right:${26}; bottom:${buttonsTop}"),
        onClick = {
            onPush()
        }
    )

    val commitsTop = buttonsTop + 3
    val commitViewportHeight = (safeHeight - commitsTop).coerceAtLeast(3)
    val maxCommitOffset = (commitLines.size - commitViewportHeight).coerceAtLeast(0)
    val clampedCommitScroll = commitScroll.coerceIn(0, maxCommitOffset)
    val commitText = commitLines.drop(clampedCommitScroll).take(commitViewportHeight).joinToString("\n") {
        it.take(contentWidth - 1)
    }
    val commitTextBox = DOMNode(
        tag = "git-commits",
        text = commitText,
        style = StyleSet.Companion.parse("left:0; top:${commitsTop}; right:${contentWidth - 1}; bottom:${safeHeight - 1}"),
        id = "git-commits",
    )
    val commitScrollbar = VerticalScrollBar(
        parent="commitTextBox",
        tree = tree,
        style = StyleSet.Companion.parse("left:${0}; top:${commitsTop}; right:${contentWidth + 1}; bottom:${safeHeight - 1}"),
        contentHeight = commitLines.size.coerceAtLeast(commitViewportHeight),
        scrollOffset = clampedCommitScroll,
        onScrollTo = { newOffset -> setCommitScroll(newOffset.coerceIn(0, maxCommitOffset)) }
    )

    DOMNode(
        tag = "git-panel",
        style = style,
        id = "git-panel",
        children = listOf(
            header,
            statusBox,
            splitter1,
            // messageLabel,
            messageArea,
            commitBtn,
            tagBtn,
            pushBtn,
            commitTextBox,
            commitScrollbar
        ),
        onMouseScroll = { ev ->
            val so = (commitScroll + 3 * (ev.scrollDelta ?: 0)).coerceIn(-3, commits.size + 5)
            setCommitScroll(so)
            false
        }
    )
}

private val commitDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault())
