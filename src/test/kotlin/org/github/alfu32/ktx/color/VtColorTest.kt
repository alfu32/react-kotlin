package org.github.alfu32.ktx.color

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VtColorTest {
    @BeforeEach
    fun setUp() {
        //TODO("Not yet implemented")
    }

    @AfterEach
    fun tearDown() {
        //TODO("Not yet implemented")
    }

    @Test
    fun testAdjust() {
        val c = VtColor(0x12.toUByte(),0x34.toUByte(),0x56.toUByte())
        val z = (1 .. 255).toList().map { it.toUByte() }
        z.map{
            val d100=c.adjust(it,100)
            val d50=c.adjust(it,50)
            val d_50=c.adjust(it,-50)
            val d_100=c.adjust(it,-100)
            println("""--- $it ---------------------------------------------------------""")
            println("""  - $it -> adjust("100")  == ${d100}, delta ${d100-it} ${(d100-it)*100.toUByte()/it}% """)
            println("""  - $it -> adjust("50")   == ${d50}, delta ${d50-it} ${(d50-it)*100.toUByte()/it}% """)
            println("""  - $it -> adjust("-50")  == ${d_50}, delta ${d_50-it} ${(d_50-it)*100.toUByte()/it}% """)
            println("""  - $it -> adjust("-100") == ${d_100}, delta ${d_100-it} ${(d_100-it)*100.toUByte()/it}% """)
        }
    }
    @Test
    fun testTimesOperator() {
        val bg_top = VtColor.from(0x223388)
        val bg_panel = VtColor.from(0x223388)*30
        val bg_splitter = VtColor.from(0x223388)*100*-50
        val bg_editor = VtColor.from(0x223388)*-30
        val bg_status = VtColor.from(0x223388)*100*-70
        println("bg_top : ${bg_top.toInt().toString(16)}")
        println("bg_panel : ${bg_panel.toInt().toString(16)}")
        println("bg_splitter : ${bg_splitter.toInt().toString(16)}")
        println("bg_editor : ${bg_editor.toInt().toString(16)}")
        println("bg_status : ${bg_status.toInt().toString(16)}")
        println(listOf(
            VtColor.from(0x223388),
            VtColor.from(0x636fab),
            VtColor.from(0x808080),
            VtColor.from(0x182460),
            VtColor.from(0x4d4d4d),
        ))
    }
    @Test
    fun testToHex() {
        val c = VtColor(0x12.toUByte(),0x34.toUByte(),0x56.toUByte())
        println(c.toHex())
        assertEquals("#123456",c.toHex())
    }
    @Test
    fun testToInt() {
        val c = VtColor(0x12.toUByte(),0x34.toUByte(),0x56.toUByte())
        println(c.toInt())
        assertEquals(0x123456,c.toInt())
    }
    @Test
    fun testFromInt() {
        val c = VtColor.from(0x123456)
        println(c.toHex())
        assertEquals("#123456",c.toHex())
    }
    @Test
    fun testFromHex() {
        val c = VtColor.from("#123456")
        println(c.toHex())
        assertEquals("#123456",c.toHex())
    }

}