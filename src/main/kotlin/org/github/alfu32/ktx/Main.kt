package org.github.alfu32.ktx

import org.github.alfu32.ktx.components.CodeEditor
import org.github.alfu32.ktx.components.FileTreeComponent
import org.github.alfu32.ktx.components.GitComponent
import org.github.alfu32.ktx.components.VerticalTabsHost
import org.github.alfu32.ktx.lib.TextBuffer
import react.renderer.AnsiCanvasRenderer
import react.renderer.CanvasRenderer
import react.ComponentTreeManager
import react.DOMNode
import react.renderer.NoopRenderer
import react.renderer.StringSnapshotRenderer
import react.StyleSet
import react.renderComponent
import react.runApp
import react.runCommand
import java.io.File
import java.time.LocalTime
import kotlin.String

/* =====================================================================
   APP DEMO (header + sidebar + splitter + content + status)
   Uses HookContext.useState for per-instance state.
   ===================================================================== */
fun App(tree: ComponentTreeManager, cols: Int, rows: Int): DOMNode =
    renderComponent(tree) {
        // --- Global-ish app state stored in hook slots
        val (splitterPos, setSplitterPos) = useState { 40 }
        val (dragging, setDragging) = useState { false }
        val (dragStartX, setDragStartX) = useState { 0 }
        val (dragStartSplit, setDragStartSplit) = useState { splitterPos }
        val (status, setStatus) = useState { "Ready" }
        val (selectedFileTreeEntry, setSelectedFileTreeEntry) = useState<FileTreeEntry?> { null }
        val (editorBuffer, _) = useState { TextBuffer() }
        val (loadedPath, setLoadedPath) = useState { "" }
        val (eventType, setEventType) = useState { "" }
        val (wheelDelta, setWheelDelta) = useState { 0 }
        val (mouseAbs, setMouseAbs) = useState { Pair(0, 0) }
        val (mouseRel, setMouseRel) = useState { Pair(0, 0) }
        val statText = "Pos:$splitterPos,drag:$dragging,StartX:$dragStartX,Split:$dragStartSplit"

        // --- Layout math
        val minPanelWidth = 20
        val maxPanelWidth = (cols - 4).coerceAtLeast(minPanelWidth)
        val clampedSplit = splitterPos.coerceIn(minPanelWidth, maxPanelWidth)
        val mainHeight = rows - 2
        val tabContentWidth = (clampedSplit - 5).coerceAtLeast(1)
        val workspaceRoot = System.getProperty("user.dir") ?: "."
        fun currentBranch(): String =
            runCatching { runCommand("sh", "-c", "git rev-parse --abbrev-ref HEAD")?.trim().orEmpty() }.getOrDefault("")
        if (selectedFileTreeEntry?.typ == "file" && selectedFileTreeEntry.fullPath != loadedPath) {
            val content = runCatching { File(selectedFileTreeEntry.fullPath).readText() }
                .getOrElse { err -> "Unable to read file:\\n${err.message ?: err.toString()}" }
            editorBuffer.loadText(content)
            setLoadedPath(selectedFileTreeEntry.fullPath)
        }
        val time = LocalTime.now().withNano(0)
        val language = when (loadedPath.substringAfterLast('.', "")) {
            "kt" -> "Kotlin"
            "java" -> "Java"
            "md" -> "Markdown"
            "py" -> "Python"
            else -> "Text"
        }
        val topStatusLine = listOf(
            "$time",
            currentBranch(),
            workspaceRoot.replace("/home/devlin", "~"),
            "Event: $eventType ",
            "${mouseAbs.first},${mouseAbs.second} rel ${mouseRel.first},${mouseRel.second}",
            " roll: $wheelDelta ",
            "${selectedFileTreeEntry?.fullPath?.replace(workspaceRoot, "")} $language"
        )

        // --- Components
        val header = DOMNode(
            tag = "header",
            id = "header",
            text = " Kotlin TUI Demo (~=quit) ${topStatusLine.joinToString(" | ")}",
            style = StyleSet.parse("left:0; top:0; right:${cols - 1}; bottom:0"),
            onMouseMove = { ev ->
                // setStatus("$statText,header,hover,x${ev.x},y${ev.y}")
            }
        )
        val tt = 55
        val sidebar = VerticalTabsHost(
            tree = tree,
            style = StyleSet.parse("left:0; top:0; right:${clampedSplit - 1}; bottom:${mainHeight - 1}"),
            tabs = linkedMapOf(
                "Files" to { _ ->
                    FileTreeComponent(
                        tag = "files-tab",
                        id = "files-tab",
                        tree = tree,
                        rootDir = workspaceRoot,
                        selected = selectedFileTreeEntry,
                        style = StyleSet.parse("left:0; top:0; right:${tabContentWidth - 1}; bottom:${mainHeight - 1}"),
                        onFileSelected = { entry ->
                            // setStatus("File Selected ${entry.fullPath}")
                            setSelectedFileTreeEntry(entry)
                        },
                        onFolderSelected = { entry ->
                            // setStatus("Folder Selected ${entry.fullPath}")
                            setSelectedFileTreeEntry(entry)
                        }
                    )
                },
                "Git" to { _ ->
                    GitComponent(
                        tree = tree,
                        workspaceRoot = workspaceRoot,
                        style = StyleSet.parse("left:0; top:0; right:${tabContentWidth}; bottom:${mainHeight - 1}")
                    )
                },
                "Settings" to { _ ->
                    DOMNode(
                        tag = "logs-tab",
                        text = "Logs\n[recent events]",
                        style = StyleSet.parse("left:0; top:0; right:${tabContentWidth}; bottom:${mainHeight - 1}"),
                        id = "logs-tab",
                    )
                }
            ),
            onTabChanged = { name -> /*setStatus("Tab -> $name")*/ }
        )

        // Splitter with simple drag logic in-place
        val splitter = DOMNode(
            tag = "splitter",
            id = "splitter",
            text = "⣿\n".repeat(mainHeight),
            style = StyleSet.parse("left:${clampedSplit}; top:0; right:${clampedSplit}; bottom:${mainHeight - 1}"),
            onMouseDown = { ev ->
                val mx = ev.x ?: return@DOMNode
                setDragging(true)
                setDragStartX(mx)
                setDragStartSplit(clampedSplit)
                // setStatus("Splitter grab @${ev.x},${ev.y}")
            },
        )

        val content = CodeEditor(
            tree = tree,
            buffer = editorBuffer,
            style = StyleSet.parse("left:${clampedSplit + 1}; top:0; right:${cols - 2}; bottom:${mainHeight - 1}"),
            filePath = loadedPath,
            language = language,
            onChange = { _ -> /*setStatus("Edited ${loadedPath}")*/ },
            onStateChange = { state ->
                val statusLine = listOf(
                    "${
                        state.filePath.replace(
                            workspaceRoot,
                            ""
                        )
                    } ${state.language} line ${state.cursorLine + 1}:${state.cursorColumn + 1} sel=${state.selection.length}"
                )
                setStatus(statusLine.joinToString(" | "))
            }
        )

        val statusBar = DOMNode(
            tag = "footer",
            id = "footer",
            text = "$status | split=$clampedSplit drag=$dragging",
            style = StyleSet.parse("left:0; top:${rows - 1}; right:${cols - 1}; bottom:${rows - 1}"),
            onMouseMove = { ev ->
                // setStatus("$statText,footer,hover,x${ev.x},y${ev.y}")
            }
        )

        val mainArea = DOMNode(
            tag = "main-area",
            id = "main-area",
            style = StyleSet.parse("left:0; top:1; right:${cols - 1}; bottom:${rows - 2}"),
            children = listOf(sidebar, splitter, content),
            onMouseDown = {
                setEventType(it.kind)
                setWheelDelta(it.scrollDelta ?: 0)
                false
            },
            onMouseMove = { ev ->

                val mx = ev.x ?: return@DOMNode
                ev.y?.let { setMouseAbs(mx to it) }
                ev.relX?.let { rx -> ev.relY?.let { ry -> setMouseRel(rx to ry) } }

                if (dragging) {
                    val dx = mx - dragStartX
                    setSplitterPos((dragStartSplit + dx).coerceIn(minPanelWidth, maxPanelWidth))
                    setEventType("dragging")
                    // setStatus("Splitter drag ${mx},${ev.y}")
                } else {
                    setEventType(ev.kind)
                    // setStatus("$statText,main-area,x${ev.x},y${ev.y}")
                }
                setWheelDelta(ev.scrollDelta ?: 0)
                true
            },
            onMouseUp = { ev ->
                setDragging(false)
                setEventType(ev.kind)
                false
            },
            onMouseScroll = {
                setEventType(it.kind)
                setWheelDelta(it.scrollDelta ?: 0)
                false
            },
            onResize = {
                setEventType(it.kind)
                setWheelDelta(it.scrollDelta ?: 0)
                false
            },
            onKeyUp = {
                setEventType(it.kind)
                setWheelDelta(it.scrollDelta ?: 0)
                false
            },
            onKeyDown = {
                setEventType(it.kind)
                setWheelDelta(it.scrollDelta ?: 0)
                false
            },
            onFocusLost = {
                setEventType(it.kind)
                setWheelDelta(it.scrollDelta ?: 0)
                false
            },
            onFocusGained = {
                setEventType(it.kind)
                setWheelDelta(it.scrollDelta ?: 0)
                false
            },
        )

        // Root composes everything
        DOMNode(
            tag = "root",
            style = StyleSet.parse("left:0; top:0; right:${cols - 1}; bottom:${rows - 1}"),
            id = "root",
            children = listOf(header, mainArea, statusBar),
        )
    }

/* =====================================================================
   APPLICATION ENTRY POINT
   ===================================================================== */

fun main(args: Array<String>) {
    // Swap renderer implementation here:
    //  - StringSnapshotRenderer: single-frame render, prints buffer, exits
    //  - NoopRenderer: single-frame render, no output
    //  - AnsiCanvasRenderer: interactive loop (ensure your terminal is in raw mode)

    val renderer: CanvasRenderer = AnsiCanvasRenderer(initialCols = 120, initialRows = 40)
    val maxFrames = if (renderer is StringSnapshotRenderer || renderer is NoopRenderer) 1 else null

    runApp(
        renderer = renderer,
        maxFrames = maxFrames?.toULong(),
        styleFiles = (listOf("styles/app.css") + args),
    ) { tree: ComponentTreeManager, cols: Int, rows: Int ->
        App(tree, cols, rows)
    }.apply {
        onExit = {
            when (renderer) {
                is StringSnapshotRenderer -> println(renderer.snapshot())
            }
        }
        onError = { println(it) }
    }
}
