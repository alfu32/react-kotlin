package org.github.alfu32.ktx.color

/**
 * RGB color used by VtDrawingContext.
 * Components are in the inclusive range [0, 255].
 */
data class VtColor(
    val r: UByte,
    val g: UByte,
    val b: UByte
){
    companion object {
        fun from(rgb: Int):VtColor {
            val r=(rgb shr 16 and 0xFF).toUByte()
            val g=(rgb shr 8 and 0xFF).toUByte()
            val b=(rgb shr 0 and 0xFF).toUByte()
            return VtColor(r=r, g=g, b=b)
        }
        fun from(rgb: String):VtColor {
            val tt=rgb.replace("#","").toInt(16)
            return from(tt)
        }
    }
    fun toHex():String{
        return "#${r.toString(16)}${g.toString(16)}${b.toString(16)}"
    }
    fun toInt(): Int{
        return ((r.toLong() shl 16) + (g.toLong() shl 8) + (b.toLong() shl 0)).toInt()
    }
    fun adjust(v: UByte,p:Byte):UByte{
        if(p<0) {
           val clampedP =if(p*p > 10000) -100 else p
           val adjustment = clampedP.toInt() * v.toInt() / 100
           return (v.toInt() + adjustment).toUByte()
        } else {
            val clampedP =if(p*p > 10000) 100 else p
            val adjustment = clampedP.toInt() * (256-v.toInt()) / 100 - 1
            return (v.toInt() + adjustment).toUByte()
        }
    }
    operator fun times(proportion: Byte): VtColor {
        return VtColor(
            r = adjust(r, proportion),
            g = adjust(g, proportion),
            b = adjust(b, proportion),
        )
    }
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