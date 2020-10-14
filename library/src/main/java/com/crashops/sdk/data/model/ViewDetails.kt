package com.crashops.sdk.data.model

import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import org.json.JSONArray
import org.json.JSONObject

class ViewDetails {
    class Keys {
        companion object {
            const val CLASS_NAME = "className"
            const val POSITION = "position"
            const val DIMENSIONS = "dimensions"
            const val CHILDREN = "children"
            const val DEPTH = "depth"
        }
    }

    companion object {
        /**
         * This must run on the main thread.
         */
        @MainThread
        fun extract(view: View): ViewDetails {
            return ViewDetails(view, 0)
        }

        fun from(jsonObject: JSONObject): ViewDetails? {
            val details = ViewDetails(jsonObject)
            return if (details.className.isNotEmpty()) {
                details
            } else {
                null
            }
        }
    }

    val className: String
    var position: Position = Position(0f,0f)
        private set
    var dimensions: Size = Size(0,0)
        private set
    var children: List<ViewDetails> = arrayListOf()
        private set
    val depth: Int

    constructor(v: View, depth: Int) {
        className = v.javaClass.simpleName
        position = Position(v.x, v.y)
        dimensions = Size(v.width, v.height)
        children = v.mapChildren {
            ViewDetails(it, depth + 1)
        }
        this.depth = depth
    }

    private constructor(json: JSONObject) {
        className = json.optString(Keys.CLASS_NAME)
        json.optJSONObject(Keys.POSITION)?.let {
            position = Position(it.optDouble("x").toFloat(), it.optDouble("y").toFloat())
        }
        json.optJSONObject(Keys.DIMENSIONS)?.let {
            dimensions = Size(it.optInt("width"), it.optInt("height"))
        }
        // These are only the initial details, we should refresh it when sizes will be available
        json.optJSONArray(Keys.CHILDREN)?.let {
            val updatedChildren: ArrayList<ViewDetails> = arrayListOf()
            for (jsonObject in it.iterator()) {
                ViewDetails.from(jsonObject)?.let { parsed ->
                    updatedChildren.add(parsed)
                }
            }

            children = updatedChildren
        }
        depth = json.optInt(Keys.DEPTH)
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

private fun JSONArray.iterator(): JSONArrayIterator {
    return JSONArrayIterator(this)
}

class JSONArrayIterator(private val jsonArray: JSONArray) : Iterator<JSONObject> {
    var currentIndex = 0
    override fun hasNext(): Boolean {
        return currentIndex < jsonArray.length()
    }

    override fun next(): JSONObject {
        val currentObject = jsonArray.getJSONObject(currentIndex)
        currentIndex += 1
        return currentObject
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
