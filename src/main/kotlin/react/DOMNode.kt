package react

data class DOMNode(
    val tag: String,
    val text: String? = null,
    val styleId: String? = null,
    val style: StyleSet = StyleSet(),
    val id: String? = null,
    var hasFocus: Boolean = false,

    // Event callbacks — all get UIEvent
    val onMouseDown: UIEventHandler? = null,
    val onMouseUp: UIEventHandler? = null,
    val onMouseMove: UIEventHandler? = null,
    val onMouseScroll: UIEventHandler? = null,
    val onKeyDown: UIEventHandler? = null,
    val onKeyUp: UIEventHandler? = null,
    val onFocusGained: UIEventHandler? = null,
    val onFocusLost: UIEventHandler? = null,
    val onResize: UIEventHandler? = null,

    val children: List<DOMNode> = emptyList(),
    val key: String? = "",
    val visible: Boolean = true,
) {
    fun boundingBox() = style.boundingBox()
    fun contains(node: DOMNode) = boundingBox().contains(node.boundingBox())


    fun eachPre(parentLevel: Int=0, parentIndex: Int=0, block: (node: DOMNode, level: Int, index: Int)->Unit){
        block(this,parentLevel,parentIndex)
        var childIndex=0
        for (child in this.children) {
            child.eachPre(parentLevel=parentLevel+1,parentIndex=childIndex,block)
            childIndex+=1
        }
    }
    fun eachPost(parentLevel: Int=0, parentIndex: Int=0, block: (node: DOMNode, level: Int, index: Int)->Unit){
        var childIndex=0
        for (child in this.children) {
            child.eachPost(parentLevel=parentLevel+1,parentIndex=childIndex,block)
            childIndex+=1
        }
        block(this,parentLevel,parentIndex)
    }
    fun eachLevel(parentLevel: Int=0, parentIndex: Int=0, block: (node: DOMNode, level: Int, index: Int)->Unit){
        var childIndex=0
        for (child in this.children) {
            child.eachPost(parentLevel=parentLevel+1,parentIndex=childIndex,block)
            childIndex+=1
        }
        block(this,parentLevel,parentIndex)
    }
}