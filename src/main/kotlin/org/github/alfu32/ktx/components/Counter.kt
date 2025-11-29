package org.github.alfu32.ktx.components

import react.ComponentTreeManager
import react.DOMNode
import react.renderComponent

fun Counter(tree: ComponentTreeManager, key: String? = null): DOMNode =
    renderComponent(tree, key) {
        val (count, setCount) = useState { 0 }
        Button(
            text = "Count: $count",
            onClick = { _ -> setCount(count + 1) }
        )
    }