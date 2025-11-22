
---

# **Terminal UI Reactive Framework (Kotlin)**

A lightweight, pure-Kotlin, fully reactive terminal UI framework modeled on functional components, hooks, and declarative DOM trees.
Supports rendering to VT-compatible terminal canvases, component state, event handling, and deterministic identity across re-renders.

---

# **Table of Contents**

1. [Overview](#overview)
2. [Goals](#goals)
3. [Non-Goals / Limitations](#limitations)
4. [Architecture](#architecture)

   * [Render Loop](#render-loop)
   * [Component Identity](#component-identity)
   * [Hooks](#hooks)
   * [DOM Nodes](#dom-nodes)
   * [Style System](#style-system)
   * [Renderer Interface](#renderer-interface)
   * [Unified Event Model](#unified-event-model)
5. [Layout Model (Absolute-Edge)](#layout-model)
6. [Event Dispatch Semantics](#event-dispatch-semantics)
7. [Creating Components](#creating-components)

   * [State Management](#state-management)
   * [Composition](#composition)
   * [List Rendering / Keys](#list-rendering)
8. [Building a VT Application (Boilerplate)](#boilerplate)
9. [Examples](#examples)
10. [Testing](#testing)
11. [Extending the Framework](#extending-the-framework)

---

# **Overview**

This framework provides:

* Declarative functional components
* React-style `useState` hook
* Component identity across renders
* A DOM-like tree for terminal UI
* A pluggable `CanvasRenderer` interface
* Full mouse/keyboard/resize event handling
* A stylesheet system with hierarchical style merging
* Absolute-edge layout (top/left/bottom/right)
* Hit-testing and focus propagation
* Deterministic rendering and event dispatch

The framework never mutates or diff-patches nodes—each render produces a **fresh DOM tree**, and identity is preserved via component instances.

---

# **Goals**

* Keep core small and deterministic
* Clean functional API for component authors
* Zero magic layout rules (you control coordinates)
* Renderer-agnostic (back buffer, ANSI renderer, mock, etc.)
* Correct identity and state across full-tree re-render
* Explicit architecture: nothing hidden, nothing implicit

---

# **Limitations**

* **No automatic layout engine**
  You must explicitly position nodes via styles.

* **No diffing**
  Full re-render every frame.

* **No async hooks or effects**
  Future extension.

* **Focus model is minimal**
  You can extend it easily.

* **Requires a real renderer implementation**
  Provided file defines only the interface, not ANSI renderer.

---

# **Architecture**

## Render Loop

The runtime performs:

1. Poll event from renderer (blocking or not).
2. Dispatch event to DOM tree (hit-test, focus rules).
3. Begin new frame.
4. Build entire component tree by evaluating functional components.
5. End frame → finalize component instance mapping.
6. Clear renderer.
7. Draw DOM tree.
8. Flush renderer output.

Repeat forever.

---

## Component Identity

Because every frame re-creates the DOM, the framework stores component state in **ComponentInstance** objects keyed by:

* call site ID (hash of line number)
* parent instance
* user-provided key (for lists)
* child position

This ensures stable identity across renders, even inside loops.

---

## Hooks

Only one hook exists currently:

### `useState(initial: () -> T): Pair<T, (T)->Unit>`

* Must be called in the same order each render.
* One slot per hook call in component instance.
* Purely functional interface.

Example:

```kotlin
val (count, setCount) = useState { 0 }
```

---

## DOM Nodes

```kotlin
data class DOMNode(
    val tag: String,
    val text: String? = null,
    val styleId: String? = null,
    val style: StyleSet = StyleSet(),
    val id: String? = null,

    val onMouseDown: ((UIEvent) -> Unit)? = null,
    val onMouseUp:   ((UIEvent) -> Unit)? = null,
    val onMouseMove: ((UIEvent) -> Unit)? = null,
    val onMouseScroll: ((UIEvent) -> Unit)? = null,
    val onKeyDown: ((UIEvent) -> Unit)? = null,
    val onKeyUp:   ((UIEvent) -> Unit)? = null,
    val onFocusGained: ((UIEvent) -> Unit)? = null,
    val onFocusLost:   ((UIEvent) -> Unit)? = null,
    val onResize: ((UIEvent) -> Unit)? = null,

    val children: List<DOMNode> = emptyList()
)
```

---

## Style System

You provided a CSS-inspired style block system:

* Hierarchical selectors split by dots
* `mergeFrom()` merges non-null fields
* `null = 0` rule for coordinates
* Only *absolute edges*: `top`, `left`, `bottom`, `right`
* Coordinates are **relative to parent**

---

## Renderer Interface

```kotlin
interface CanvasRenderer {
    fun clear()
    fun setColor(r: Int, g: Int, b: Int)
    fun setBackgroundColor(r: Int, g: Int, b: Int)
    fun bold(enabled: Boolean)
    fun italic(enabled: Boolean)
    fun underline(enabled: Boolean)
    fun blink(enabled: Boolean)
    fun drawRect(x: Int, y: Int, width: Int, height: Int)
    fun drawText(x: Int, y: Int, text: String)
    fun setCursorPosition(x: Int, y: Int)
    fun flush()

    fun pollEvent(): UIEvent?
    fun tryPollEvent(): UIEvent?
}
```

You will plug in a real terminal renderer here (ANSI, Termux, etc.).

---

## Unified Event Model

All events share a single structure:

```kotlin
data class UIEvent(
    val kind: String,
    val x: Int? = null,
    val y: Int? = null,
    val button: Int? = null,
    val scrollDelta: Int? = null,
    val key: String? = null,
    val focusId: String? = null,
    val cols: Int? = null,
    val rows: Int? = null
)
```

Examples:

* `UIEvent("mouse_down", x=10, y=5, button=1)`
* `UIEvent("key_down", key="Enter")`
* `UIEvent("resize", cols=120, rows=40)`

---

# **Layout Model**

This is very simple and explicit:

* All layout is *absolute* relative to parent anchor.
* `null = 0`.

For node N with style:

```
top: T
left: L
bottom: B
right: R
```

All relative:

```
x1 = parentX + L
y1 = parentY + T
x2 = parentX + R
y2 = parentY + B
width  = x2 - x1
height = y2 - y1
```

If you supply inconsistent values, it is your responsibility.

---

# **Event Dispatch Semantics**

### Mouse

Hit-tested against node rectangle.
Deepest-first traversal (children get event first).

### Keyboard

Delivered to **all nodes with onKeyDown/onKeyUp**.
(You can implement focus filtering as needed.)

### Resize

Delivered to all nodes with `onResize`.

---

# **Creating Components**

A component is a **pure function** returning a DOMNode:

```kotlin
fun counterComponent(tree: ComponentTreeManager, key: String? = null): DOMNode =
    renderComponent(tree, key) {
        val (count, setCount) = useState { 0 }
        Button(
            text = "Count: $count",
            onClick = { _ -> setCount(count + 1) }
        )
    }
```

---

## State Management

`useState` persists per component instance:

```kotlin
val (name, setName) = useState { "hello" }
```

---

## Composition

Components can nest freely:

```kotlin
fun panel(tree: ComponentTreeManager): DOMNode =
    DOMNode(
        tag = "panel",
        style = StyleSet(top=0, left=0, bottom=20, right=80),
        children = listOf(
            counterComponent(tree),
            counterComponent(tree)
        )
    )
```

Each call is a distinct instance with distinct state.

---

## List Rendering (Keys)

Keys preserve identity across reorderings:

```kotlin
fun listOfCounters(tree: ComponentTreeManager, nums: List<Int>) =
    DOMNode(
        tag = "list",
        children = nums.map { n ->
            counterComponent(tree, key = n.toString())
        }
    )
```

---

# **Boilerplate: Building a VT Application**

Below is the minimal app skeleton you will use.

```kotlin
fun main() {
    val tree = ComponentTreeManager()
    val renderer = MyAnsiRenderer() // you implement this

    while (true) {
        val event = renderer.pollEvent()
        if (event != null) {
            dispatchEvent(tree, event) // you call dispatchEventToDom on root
        }

        tree.beginFrame()
        val root = app(tree)        // Your root component
        tree.endFrame()

        renderer.clear()
        renderDomTree(renderer, root)
        renderer.flush()
    }
}
```

### Root Component Example

```kotlin
fun app(tree: ComponentTreeManager): DOMNode =
    renderComponent(tree) {
        DOMNode(
            tag = "root",
            style = StyleSet(top=0, left=0, right=80, bottom=24),
            children = listOf(
                counterComponent(tree, key="c1"),
                counterComponent(tree, key="c2"),
                listOfCounters(tree, listOf(1,2,3))
            )
        )
    }
```

This example demonstrates:

* composition
* two instances of same component
* list rendering
* keyed identity

---

# **Examples**

### Minimal button

```kotlin
fun HelloButton(tree: ComponentTreeManager): DOMNode =
    renderComponent(tree) {
        Button(
            text = "Click Me!",
            onClick = { println("clicked!") },
            style = StyleSet(left=2, top=1, right=12, bottom=2)
        )
    }
```

---

### Mouse Move Event

```kotlin
fun MouseTracker(tree: ComponentTreeManager): DOMNode =
    renderComponent(tree) {
        val (pos, setPos) = useState { "0,0" }

        DOMNode(
            tag = "tracker",
            text = "Mouse: $pos",
            onMouseMove = { e ->
                val x = e.x ?: 0
                val y = e.y ?: 0
                setPos("$x,$y")
            },
            style = StyleSet(left=1, top=1, right=40, bottom=2)
        )
    }
```

---

# **Testing**

The single-file library includes Kotlin `kotlin.test` unit tests that verify:

* state persistence across frames
* identity preservation in lists
* independent instances at same call site

You can add snapshot or renderer tests using a mock renderer.

---

# **Extending the Framework**

Possible additions:

* ANSI-based renderer (bold, underline, colors, cursor control)
* Back-buffer renderer (double-buffered VT UI)
* Focus manager (tab cycling, default focus, focus rings)
* Border renderer (`borderSet`, line-set graphics)
* Stylesheet cascade for entire DOM recursion
* Memoized components (skip rendering when values equal)
* Derived layout helpers (center, grid, flex-like helpers)

---

# **End**

This documentation covers the entire framework, its architecture, usage, and design intent.

If you want a **full ANSI renderer implementation**, **string snapshot renderer**, or **focus manager**, I can generate those next.
