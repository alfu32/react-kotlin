package org.github.alfu32.ktx

/*
===============================================================
  FILE TREE INTERFACE
===============================================================
  Describes the required API for a generic line-based text buffer
  abstraction, independent of UI toolkit or rendering layer.
===============================================================
*/
interface IFileTree {
    val root: String

    fun toggle(path: String)
    fun flattened(): List<FileTreeEntry>
    fun refreshOpenNodes()
}

/*
===============================================================
  FILE TREE INTERFACE DATA TYPES
===============================================================
*/
data class FileTreeEntry(
    val name: String,
    val typ: String,      // "file" or "folder"
    val padding: Int,
    val fullPath: String,
    val isOpen: Boolean
)

class FileTreeItem(
    val name: String,
    val fullPath: String,
    val isDir: Boolean,
    var isOpen: Boolean = false,
    var children: List<String> = emptyList()
)