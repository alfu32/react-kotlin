package react

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StyleSetTest {
    @BeforeEach
    fun setUp() {
        // TODO("Not yet implemented")
    }

    @AfterEach
    fun tearDown() {
        // TODO("Not yet implemented")
    }

    @Test
    fun test_offset() {

        val contentWidth = 20
        var ss = StyleSet.parse(
            "left:0; top:0; right:${contentWidth}; bottom:0"
        )
        println(ss.boundingBox())
        ss=ss.offset(7, 1)
        println(ss.boundingBox())
    }

}