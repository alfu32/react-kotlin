package org.github.alfu32.ktx.context

enum class VtKeyType {
    Character,
    ArrowUp,
    ArrowDown,
    ArrowLeft,
    ArrowRight,
    Enter,
    Escape,
    Backspace,
    Tab,
    Unknown
}

data class VtKeyEvent(
    val type: VtKeyType,
    val ch: Char? = null,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false
)

enum class VtMouseButton {
    Left,
    Middle,
    Right,
    WheelUp,
    WheelDown,
    None
}

enum class VtMouseEventKind {
    Press,
    Release,
    Drag,
    Move
}

sealed class VtEvent {
    data class Key(val key: VtKeyEvent) : VtEvent()
    data class Mouse(
        val x: Int,
        val y: Int,
        val button: VtMouseButton,
        val kind: VtMouseEventKind,
        val ctrl: Boolean = false,
        val alt: Boolean = false,
        val shift: Boolean = false
    ) : VtEvent()
    data class Resize(val width: Int, val height: Int) : VtEvent()
}

fun interface VtEventListener {
    fun onEvent(ctx: VtDrawingContext, event: VtEvent)
}