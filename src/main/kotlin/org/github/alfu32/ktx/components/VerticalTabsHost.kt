package org.github.alfu32.ktx.components

import react.ComponentTreeManager
import react.DOMNode
import react.StyleSet
import react.renderComponent

fun VerticalTabsHost(
    tree: ComponentTreeManager,
    style: StyleSet,
    tabs: LinkedHashMap<String, (ComponentTreeManager) -> DOMNode>,
    onTabChanged: (String) -> Unit,
    key: String? = null
): DOMNode = renderComponent(tree, key) {
    val hostWidth = (style.right ?: 0) - (style.left ?: 0) + 1 - 1
    val hostHeight = (style.bottom ?: 0) - (style.top ?: 0) + 1
    val stripeWidth = 6
    val buttonHeight = 3
    val initialTab = tabs.keys.firstOrNull()
    val (activeTab, setActiveTab) = useState { initialTab ?: "" }

    fun makeLabel(name: String): String {
        val short = name.take(stripeWidth).padEnd(stripeWidth, ' ')
        return buildString {
            append(" ".repeat(stripeWidth)).append('\n')
            append(short).append('\n')
            append(" ".repeat(stripeWidth))
        }
    }

    val buttons = tabs.entries.mapIndexed { idx, entry ->
        val top = idx * buttonHeight
        val bottom = top + buttonHeight - 1
        val selected = entry.key == activeTab
        val tabTag = if (selected) "tab-button::selection" else "tab-button"

        DOMNode(
            tag = tabTag,
            id = tabTag,
            key = key,
            text = makeLabel(entry.key),
            style = StyleSet.Companion.parse("left:0; top:${top}; right:${stripeWidth - 1}; bottom:${bottom}"),
            onMouseDown = {
                if (activeTab != entry.key) {
                    setActiveTab(entry.key)
                    onTabChanged(entry.key)
                }
            }
        )
    }

    val contentWidth = (hostWidth - stripeWidth).coerceAtLeast(1)
    val content = tabs[activeTab]?.invoke(tree)

    DOMNode(
        tag = "vertical-tabs-host",
        id = "vertical-tabs-host",
        style = style,
        children = listOfNotNull(
            DOMNode(
                tag = "tab-strip",
                style = StyleSet.Companion.parse(
                    "left:0; top:0; right:${stripeWidth - 1}; bottom:${
                        (hostHeight - 1).coerceAtLeast(
                            0
                        )
                    }"
                ),
                id = "tab-strip",
                children = buttons,
            ),
            content?.let {
                DOMNode(
                    tag = "tab-content-area",
                    style = StyleSet.Companion.parse(
                        "left:${stripeWidth}; top:0; right:${hostWidth - 1}; bottom:${
                            (hostHeight - 1).coerceAtLeast(
                                0
                            )
                        }"
                    ),
                    id = "tab-content-area",
                    children = listOf(it),
                )
            }
        ),
    )
}