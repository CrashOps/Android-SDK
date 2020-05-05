package com.crashops.sdk.data.model

import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread

class ViewDetails(v: View, val depth: Int) {
    companion object {
        /**
         * This must run on the main thread.
         */
        @MainThread
        fun extract(view: View): ViewDetails {
            return ViewDetails(view, 0)
        }
    }

    val className = v.javaClass.simpleName
    val position = Position(v.x, v.y)
    val dimensions = Size(v.width, v.height)
    val children: List<ViewDetails> = v.mapChildren {
        ViewDetails(it, depth + 1)
    }

    val isLeaf: Boolean
        get() = this.children.isEmpty()

    // Discussion: in cases of fragments, e.i. ViewPager, the children count
    // may change so it might be confusing as it's the same view and the children
    // are changes after every user's scroll.
    override fun equals(other: Any?): Boolean {
        if (other !is ViewDetails) return false
        if (className != other.className ||
                position != other.position ||
                dimensions != other.dimensions) return false

        if (children.size != other.children.size) return false

        var allChildrenAreEqual = true
        for (i in children.indices) {
            allChildrenAreEqual = allChildrenAreEqual && children[i] == other.children[i]
        }

        return allChildrenAreEqual
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun toString(): String {
        return "{className: '$className', size: (${dimensions.width} x ${dimensions.height}), position: (${position.x} x ${position.y})}"
    }
}

private fun <R> View.mapChildren(transform: (View) -> R): List<R> {
    val children = arrayListOf<R>()
    if (this is ViewGroup) {
        val childCount = (this as? ViewGroup)?.childCount ?: return emptyList()
        for (i in 0 until childCount) {
            children.add(transform(getChildAt(i)))
        }
    }

    return children
}
