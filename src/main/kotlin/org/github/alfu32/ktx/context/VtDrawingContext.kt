package org.github.alfu32.ktx.context

import org.github.alfu32.ktx.color.VtColor

/**
 * Kotlin interface corresponding to V's term.ui.Context drawing API.
 *
 * This abstracts a terminal drawing surface and its basic state.
 */
interface VtDrawingContext {

    var onFrame: ((VtDrawingContext) -> Unit)?

    /**
     * Number of frames rendered so far.
     * Mirrors Context.frame_count (u64).
     */
    val frameCount: Long

    /**
     * Current terminal window width in character cells.
     * Mirrors Context.window_width.
     */
    val windowWidth: Int

    /**
     * Current terminal window height in character cells.
     * Mirrors Context.window_height.
     */
    val windowHeight: Int

    /** Sets the character state to bold. */
    fun bold()

    /** Erases the entire terminal window and any saved lines. */
    fun clear()

    /**
     * Draws a dashed line segment from (x1, y1) to (x2, y2).
     */
    fun drawDashedLine(x1: Int, y1: Int, x2: Int, y2: Int)

    /**
     * Draws a rectangle with dashed lines, from top-left (x1, y1)
     * to bottom-right (x2, y2), with no fill.
     */
    fun drawEmptyDashedRect(x1: Int, y1: Int, x2: Int, y2: Int)

    /**
     * Draws a rectangle border (no fill), from top-left (x1, y1)
     * to bottom-right (x2, y2).
     */
    fun drawEmptyRect(x1: Int, y1: Int, x2: Int, y2: Int)

    /**
     * Draws a solid line segment from (x1, y1) to (x2, y2).
     */
    fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int)

    /**
     * Draws a single point at (x, y).
     */
    fun drawPoint(x: Int, y: Int)

    /**
     * Draws a filled rectangle from top-left (x1, y1)
     * to bottom-right (x2, y2).
     */
    fun drawRect(x1: Int, y1: Int, x2: Int, y2: Int)

    /**
     * Draws text starting at (x, y).
     */
    fun drawText(x: Int, y: Int, text: String)

    /**
     * Flushes the accumulated buffer to the terminal.
     */
    fun flush()

    /**
     * Makes the cursor invisible.
     */
    fun hideCursor()

    /**
     * Makes the cursor visible.
     */
    fun showCursor()

    /**
     * Draws a horizontal separator spanning the width of the screen at row y.
     */
    fun horizontalSeparator(y: Int)

    /**
     * Resets colors and text formatting to defaults.
     */
    fun reset()

    /**
     * Resets the background color to its default value.
     */
    fun resetBackgroundColor()

    /**
     * Resets the foreground color to its default value.
     */
    fun resetColor()

    /**
     * Starts the terminal UI event/render loop.
     * Mirrors Context.run().
     */
    fun run()

    /**
     * Sets the current background color for subsequent draw calls.
     */
    fun setBackgroundColor(color: VtColor)

    /**
     * Sets the current foreground color for subsequent draw calls.
     */
    fun setColor(color: VtColor)

    /**
     * Positions the cursor at (x, y).
     */
    fun setCursorPosition(x: Int, y: Int)

    /**
     * Sets the terminal window title.
     */
    fun setWindowTitle(title: String)

    /**
     * Appends text to the internal print buffer without flushing.
     */
    fun write(text: String)
}