package org.github.alfu32.ktx.color

import org.github.alfu32.ktx.ComponentTreeManager
import org.github.alfu32.ktx.DOMNode
import org.github.alfu32.ktx.UIEvent
import org.github.alfu32.ktx.counterComponent
import org.github.alfu32.ktx.listOfCounters
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/* =====================================================================
   UNIT TESTS (still fully valid)
   ===================================================================== */

class ComponentSystemTests {

    @Test fun testCounterStatePersistsBetweenFrames() {
        val tree = ComponentTreeManager()

        tree.beginFrame()
        val dom1 = counterComponent(tree)
        val click = dom1.onMouseDown!!
        tree.endFrame()

        click(UIEvent(kind="mouse_down"))

        tree.beginFrame()
        val dom2 = counterComponent(tree)
        tree.endFrame()

        assertEquals("Count: 1", dom2.text)
    }

    @Test fun testListIdentityWithKeys() {
        val tree = ComponentTreeManager()
        val values = mutableListOf(1, 2, 3)

        tree.beginFrame()
        val dom1 = listOfCounters(tree, values)
        val click2 = dom1.children[1].onMouseDown!!
        tree.endFrame()

        click2(UIEvent(kind="mouse_down"))

        values.remove(2)
        values.add(0, 2)

        tree.beginFrame()
        val dom2 = listOfCounters(tree, values)
        tree.endFrame()

        assertEquals("Count: 1", dom2.children[0].text)
    }

    @Test fun testIndependentInstancesAtSameCallSite() {
        val tree = ComponentTreeManager()

        fun doubleCounter() =
            DOMNode(
                "counter",
                children = listOf(
                    counterComponent(tree),
                    counterComponent(tree)
                ),
            )

        tree.beginFrame()
        val dom1 = doubleCounter()
        val c1 = dom1.children[0].onMouseDown!!
        val c2 = dom1.children[1].onMouseDown!!
        tree.endFrame()

        c2(UIEvent(kind="mouse_down"))

        tree.beginFrame()
        val dom2 = doubleCounter()
        tree.endFrame()

        assertEquals("Count: 0", dom2.children[0].text)
        assertEquals("Count: 1", dom2.children[1].text)
    }
}