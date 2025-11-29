package react.otherdom

import react.ContentBox
import react.otherdom.Event

data class TNode<T>(
    var value: T? = null,
) {
    var children: MutableList<TNode<T>> = mutableListOf()
    var onEvent: EventHandler ={}
    var zIndex=-1
    var isVisible=true
    var bb= ContentBox()
    companion object{
        var zIndex: Int = 0
        fun reset(){
            zIndex = 0
        }
    }
    // Secondary ctor: allow TreeNode("value") { ... }
    constructor(
        value: T,
        build: TNode<T>.() -> Unit
    ) : this(value) {
        this.zIndex = TNode.zIndex++
        this.build()
    }

    // child { TreeNode("...") { ... } }
    fun child(genNode: () -> TNode<T>) {
        children.add(genNode())
    }
    fun visible(st : Boolean){
        isVisible = st
    }
    // box { TreeNode("...") { ... } }
    fun box(genBox: () -> Array<Int> ) {
        val points = genBox()
        bb.add(points.getOrNull(0)?:0,points.getOrNull(1)?:0)
        bb.add(points.getOrNull(2)?:0,points.getOrNull(3)?:0)
    }
    fun on(type:String="any",handler: EventHandler){
        onEvent = handler
    }

    fun scanParentFirst(parentLevel: Int=0,parentIndex: Int=0,block: (node: TNode<T>, level: Int, index: Int)->Unit){
        block(this,parentLevel,parentIndex)
        var childIndex=0
        for (child in this.children) {
            child.scanParentFirst(parentLevel=parentLevel+1,parentIndex=childIndex,block)
            childIndex+=1
        }
    }
    fun scanDepthFirst(parentLevel: Int=0,parentIndex: Int=0,block: (node: TNode<T>, level: Int, index: Int)->Unit){
        var childIndex=0
        for (child in this.children) {
            child.scanDepthFirst(parentLevel=parentLevel+1,parentIndex=childIndex,block)
            childIndex+=1
        }
        block(this,parentLevel,parentIndex)
    }
    fun dispatchEventParentFirst(ev: Event){
        scanDepthFirst { node,level,index ->
            node.onEvent(ev)
        }
    }
    fun dispatchEventDepthFirst(ev: Event){
        scanDepthFirst { node,level,index ->
            node.onEvent(ev)
        }
    }
}
typealias EventHandler = (Event) -> Unit

class Event(
    var kind:String,
    var payload:String? = "",
    var propagationStoped: Boolean = false,
) {
    fun stopPropagation() {
        propagationStoped = true
    }

    override fun toString(): String {
        val hash=super.toString()
        return "{kind:$kind,payload:$payload,propagationStoped:$propagationStoped,hash:$hash}"
    }
}