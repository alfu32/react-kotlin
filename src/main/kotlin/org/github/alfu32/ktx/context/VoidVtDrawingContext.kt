package org.github.alfu32.ktx.context

import org.github.alfu32.ktx.color.VtColor

/**
 * A VtDrawingContext that performs no I/O and has fixed zero-sized dimensions.
 */
class VoidVtDrawingContext : VtDrawingContext {

    override var onFrame: ((VtDrawingContext) -> Unit)? = null

    override val frameCount: Long
        get() = 0L

    override val windowWidth: Int
        get() = 0

    override val windowHeight: Int
        get() = 0

    override fun bold() {
        // no-op
    }

    override fun clear() {
        // no-op
    }

    override fun drawDashedLine(x1: Int, y1: Int, x2: Int, y2: Int) {
        // no-op
    }

    override fun drawEmptyDashedRect(x1: Int, y1: Int, x2: Int, y2: Int) {
        // no-op
    }

    override fun drawEmptyRect(x1: Int, y1: Int, x2: Int, y2: Int) {
        // no-op
    }

    override fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int) {
        // no-op
    }

    override fun drawPoint(x: Int, y: Int) {
        // no-op
    }

    override fun drawRect(x1: Int, y1: Int, x2: Int, y2: Int) {
        // no-op
    }

    override fun drawText(x: Int, y: Int, text: String) {
        // no-op
    }

    override fun flush() {
        // no-op
    }

    override fun hideCursor() {
        // no-op
    }

    override fun showCursor() {
        // no-op
    }

    override fun horizontalSeparator(y: Int) {
        // no-op
    }

    override fun reset() {
        // no-op
    }

    override fun resetBackgroundColor() {
        // no-op
    }

    override fun resetColor() {
        // no-op
    }

    override fun run() {
        // no-op
    }

    override fun setBackgroundColor(color: VtColor) {
        // no-op
    }

    override fun setColor(color: VtColor) {
        // no-op
    }

    override fun setCursorPosition(x: Int, y: Int) {
        // no-op
    }

    override fun setWindowTitle(title: String) {
        // no-op
    }

    override fun write(text: String) {
        // no-op
    }
}