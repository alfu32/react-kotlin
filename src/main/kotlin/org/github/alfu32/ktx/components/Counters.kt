package org.github.alfu32.ktx.components

import react.ComponentTreeManager
import react.DOMNode

fun Counters(tree: ComponentTreeManager, values: List<Int>): DOMNode =
    DOMNode(
        tag = "list-counter-container",
        id = "list-counter-container",
        children = values.map { v -> Counter(tree, key = v.toString()) },
    )