package org.github.alfu32.ktx.color

import org.github.alfu32.ktx.EditorViewport
import org.github.alfu32.ktx.Position
import org.github.alfu32.ktx.lib.TextBuffer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TextBufferTest {

    private lateinit var buf: TextBuffer

    @BeforeEach
    fun setup() {
        buf = TextBuffer()
    }

    /* -------------------------------------------------------------
       BASIC LOAD & TEXT ACCESS
    ------------------------------------------------------------- */

    @Test
    fun loadTextSimple() {
        buf.loadText("hello\nworld")
        assertEquals("hello\nworld", buf.text())
        assertEquals(2, buf.clone().text().split("\n").size)
    }

    @Test
    fun loadTextPreservesTrailingNewline() {
        buf.loadText("a\nb\n")
        assertEquals(listOf("a", "b", ""), buf.clone().text().split("\n"))
    }

    /* -------------------------------------------------------------
       CURSOR MOVEMENT
    ------------------------------------------------------------- */

    @Test
    fun cursorMovementSimple() {
        buf.loadText("abc")
        buf.moveRight()
        buf.moveRight()
        assertEquals(Position(0, 2), getCursor())
        buf.moveLeft()
        assertEquals(Position(0, 1), getCursor())
    }

    @Test
    fun cursorMovementMultiLine() {
        buf.loadText("aaa\nbbb")
        buf.moveCursorTo(Position(0, 3), false)
        buf.moveRight()
        assertEquals(Position(1, 0), getCursor())
        buf.moveLeft()
        assertEquals(Position(0, 3), getCursor())
    }

    @Test
    fun cursorUpDownClampsColumn() {
        buf.loadText("short\nmuchlongerline")
        buf.moveCursorTo(Position(1, 14), false)
        buf.moveUp()
        assertEquals(Position(0, 5), getCursor())
    }

    /* -------------------------------------------------------------
       INSERTION
    ------------------------------------------------------------- */

    @Test
    fun insertSingleLine() {
        buf.loadText("abcd")
        buf.moveCursorTo(Position(0, 2), false)
        buf.insertText("XYZ")
        assertEquals("abXYZcd", buf.text())
    }

    @Test
    fun insertMultiLine() {
        buf.loadText("hello")
        buf.moveCursorTo(Position(0, 5), false)
        buf.insertText("A\nB\nC")
        assertEquals("helloA\nB\nC", buf.text())
    }

    /* -------------------------------------------------------------
       DELETION
    ------------------------------------------------------------- */

    @Test
    fun backspaceWithinLine() {
        buf.loadText("abcd")
        buf.moveCursorTo(Position(0, 2), false)
        buf.deleteBackspace()
        assertEquals("acd", buf.text())
        assertEquals(Position(0, 1), getCursor())
    }

    @Test
    fun backspaceJoinLines() {
        buf.loadText("abc\nxyz")
        buf.moveCursorTo(Position(1, 0), false)
        buf.deleteBackspace()
        assertEquals("abcxyz", buf.text())
        assertEquals(Position(0, 3), getCursor())
    }

    @Test
    fun deleteForwardWithinLine() {
        buf.loadText("abcd")
        buf.moveCursorTo(Position(0, 1), false)
        buf.deleteForward()
        assertEquals("acd", buf.text())
        assertEquals(Position(0, 1), getCursor())
    }

    @Test
    fun deleteForwardJoinLines() {
        buf.loadText("foo\nbar")
        buf.moveCursorTo(Position(0, 3), false)
        buf.deleteForward()
        assertEquals("foobar", buf.text())
    }

    /* -------------------------------------------------------------
       SELECTION / COPY / CUT / PASTE
    ------------------------------------------------------------- */

    @Test
    fun selectCopyPaste() {
        buf.loadText("hello world")
        buf.startSelection(Position(0, 6))
        buf.selectTo(Position(0, 11))
        assertTrue(buf.hasSelection())
        assertTrue(buf.copySelection())

        buf.moveCursorTo(Position(0, 5), false)
        buf.pasteClipboard()

        assertEquals("hello worldworld", buf.text())
    }

    @Test
    fun cutRemovesText() {
        buf.loadText("abcd efgh")
        buf.startSelection(Position(0, 2))
        buf.selectTo(Position(0, 7))
        assertTrue(buf.cutSelection())
        assertEquals("abgh", buf.text())
    }

    @Test
    fun selectionClear() {
        buf.loadText("abc")
        buf.startSelection(Position(0, 0))
        buf.selectTo(Position(0, 2))
        assertTrue(buf.hasSelection())
        buf.clearSelection()
        assertFalse(buf.hasSelection())
    }

    /* -------------------------------------------------------------
       WORD NAVIGATION
    ------------------------------------------------------------- */

    @Test
    fun moveWordRight() {
        buf.loadText("abc 123 xyz")
        buf.moveCursorTo(Position(0, 0), false)
        buf.moveRight(word = true)
        assertEquals(Position(0, 3), getCursor()) // end of "abc"
        buf.moveRight(word = true)
        assertEquals(Position(0, 7), getCursor()) // end of "123"
    }

    @Test
    fun moveWordLeft() {
        buf.loadText("abc 123 xyz")
        buf.moveCursorTo(Position(0, 11), false)
        buf.moveLeft(word = true)
        assertEquals(Position(0, 8), getCursor())
        buf.moveLeft(word = true)
        assertEquals(Position(0, 4), getCursor())
    }

    /* -------------------------------------------------------------
       TAB EXPANSION / VISUAL COLUMN
    ------------------------------------------------------------- */

    @Test
    fun expandTabsWorks() {
        assertEquals("    ab", TextBuffer.expandTabs("\tab"))
        assertEquals(4, TextBuffer.visualColumn("\tab", 1))
    }

    @Test
    fun actualColumnFromVisual() {
        val line = "\tab"
        val vcol = TextBuffer.visualColumn(line, 1)
        val idx = TextBuffer.actualColumn(line, vcol)
        assertEquals(1, idx)
    }

    /* -------------------------------------------------------------
       VIEWPORT SLICE
    ------------------------------------------------------------- */

    @Test
    fun viewportBasic() {
        buf.loadText("line1\nline2\nline3")
        val vp = buf.viewportSlice(
            view = EditorViewport(x = 0, y = 1, width = 5, height = 2),
            gutterWidth = 4
        )

        assertEquals(2, vp.lines.size)
        assertEquals("   2 ", vp.lines[0].gutter)
        assertEquals("line2", vp.lines[0].segments[0].text.trim())
    }

    @Test
    fun viewportHighlightsSelection() {
        buf.loadText("abcdef")
        buf.startSelection(Position(0, 2))
        buf.selectTo(Position(0, 4))

        val vp = buf.viewportSlice(
            EditorViewport(0, 0, 6, 1),
            gutterWidth = 3
        )

        val segments = vp.lines[0].segments
        assertEquals(3, segments.size)

        assertFalse(segments[0].selected)   // "ab"
        assertTrue(segments[1].selected)    // "cd"
        assertFalse(segments[2].selected)   // "ef"
    }

    /* -------------------------------------------------------------
       HELPERS
    ------------------------------------------------------------- */

    private fun getCursor(): Position {
        // Ugly reflection workaround because cursor is private
        val field = TextBuffer::class.java.getDeclaredField("cursor")
        field.isAccessible = true
        return (field.get(buf) as Position).copy()
    }
}
