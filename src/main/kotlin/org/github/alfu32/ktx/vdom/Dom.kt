package org.github.alfu32.ktx.vdom

import org.github.alfu32.ktx.color.VtColor
import org.github.alfu32.ktx.context.VtEvent
import org.github.alfu32.ktx.context.VtKeyEvent

data class DomLayout(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * Simple styling: colors + text modifiers.
 */
data class DomStyle(
    val foreground: VtColor? = null,
    val background: VtColor? = null,
    val bold: Boolean = false,
    val italic: Boolean = false
)

data class DomMouseEvent(
    val localX: Int,
    val localY: Int,
    val globalX: Int,
    val globalY: Int,
    val original: VtEvent.Mouse
)

data class DomKeyEvent(
    val key: VtKeyEvent,
    val original: VtEvent.Key
)

data class DomResizeEvent(
    val width: Int,
    val height: Int,
    val original: VtEvent.Resize
)

data class Rect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    fun contains(px: Int, py: Int): Boolean =
        px >= x && py >= y && px < x + width && py < y + height
}

/**
 * Slim DOM node.
 */
class DomNode(
    val id: String? = null,
    var text: String = "",
    var layout: DomLayout = DomLayout(0,0,200,120),
    var style: DomStyle = DomStyle(),
    var component: DomComponent? = null    // <= NEW
) {
    var parent: DomNode? = null
        internal set

    val children: MutableList<DomNode> = mutableListOf()

    var onMouseDown: ((DomMouseEvent) -> Unit)? = null
    var onMouseUp: ((DomMouseEvent) -> Unit)? = null
    var onMouseMove: ((DomMouseEvent) -> Unit)? = null

    var onKey: ((DomKeyEvent) -> Unit)? = null
    var onWindowResize: ((DomResizeEvent) -> Unit)? = null

    internal var bounds: Rect = Rect(0, 0, 0, 0)

    fun addChild(child: DomNode): DomNode {
        child.parent = this
        children += child
        return child
    }
}