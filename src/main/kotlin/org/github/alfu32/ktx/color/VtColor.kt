package org.github.alfu32.ktx.color

/**
 * RGB color used by VtDrawingContext.
 * Components are in the inclusive range [0, 255].
 */
data class VtColor(
    val r: Byte,
    val g: Byte,
    val b: Byte
){
    /**
     * Returns an ANSI 24-bit foreground color sequence, e.g. "\u001B[38;2;R;G;Bm".
     */
    fun foreground(): String {
        val rr = r.toInt() and 0xFF
        val gg = g.toInt() and 0xFF
        val bb = b.toInt() and 0xFF
        return "\u001B[38;2;$rr;$gg;${bb}m"
    }
    /**
     * Returns an ANSI 24-bit background color sequence, e.g. "\u001B[38;2;R;G;Bm".
     */
    fun background(): String {
        val rr = r.toInt() and 0xFF
        val gg = g.toInt() and 0xFF
        val bb = b.toInt() and 0xFF
        return "\u001B[48;2;$rr;$gg;${bb}m"
    }
}