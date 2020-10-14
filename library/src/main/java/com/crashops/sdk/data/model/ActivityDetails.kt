package com.crashops.sdk.data.model

import android.app.Activity
import android.view.View
import com.crashops.sdk.COHostApplication
import com.crashops.sdk.data.Repository
import com.crashops.sdk.data.toJson
import com.crashops.sdk.util.SdkLogger
import com.crashops.sdk.util.Utils
import org.json.JSONArray
import org.json.JSONObject

class ActivityDetails: View.OnLayoutChangeListener {
    class Keys {
        companion object {
            const val NAME = "name"
            const val PACKAGE = "package"
            const val TIMESTAMP = "timestamp"
            const val VIEWS = "views"
        }
    }

    companion object {
        fun from(jsonString: String): ActivityDetails? {
            return jsonString.toJson()?.let {
                val details = ActivityDetails(it)
                return if (details.timestamp > 0) {
                    details
                } else {
                    null
                }
            }
        }

        private val TAG: String = ActivityDetails::class.java.simpleName
    }

    constructor(json: JSONObject) {
        name = json.optString(Keys.NAME)
        packageName = json.optString(Keys.PACKAGE)
        timestamp = json.optLong(Keys.TIMESTAMP)
        // These are only the initial details, we should refresh it when sizes will be available
        json.optJSONObject(Keys.VIEWS)?.let {
            ViewDetails.from(it)?.let { parsed ->
                viewDetails = parsed
            }
        }
    }

    constructor(activity: Activity) {
        activity.window.decorView.addOnLayoutChangeListener(this)
        name = activity.localClassName
        packageName = activity.packageName
        timestamp = Utils.getCurrentTimestamp()
        // These are only the initial details, we should refresh it when sizes will be available
        viewDetails = ViewDetails.extract(activity.window.decorView)

        if (COHostApplication.shared().isHostAppDebuggable) {
            val stringBuilder = java.lang.StringBuilder()
            viewDetails?.print(stringBuilder)
            SdkLogger.log(TAG, "\n${stringBuilder}")
        }
    }

    var name: String = ""
        private set

    var packageName: String = ""
        private set

    var timestamp: Long = 0
        private set

    var viewDetails: ViewDetails? = null
        private set

    override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        v?.let { changedView ->
            // Copying the tree to simpler classes must occur on the Main Thread to avoid changes while running.
            val updatedViewDetails = ViewDetails.extract(changedView)
            COHostApplication.shared().runInBackgroundThread {
                // This comparision won't run the Main Thread to avoid janky UI
                // as it compares both trees.
                if (viewDetails == updatedViewDetails) {
                    // No more changes - stop observing changes
                    // (even animations will change layout so it's not recommended to endlessly observe these changes)
                    COHostApplication.shared().runOnUiThread {
                        changedView.removeOnLayoutChangeListener(this)
                    }
                } else {
                    viewDetails = updatedViewDetails
                    Repository.instance.persistBreadcrumb(this)
                }
            }
        } ?: run {
            v?.removeOnLayoutChangeListener(this)
        }
    }

    fun stopObserving(activity: Activity) {
        activity.window.decorView.removeOnLayoutChangeListener(this)
    }
}

private fun ViewDetails.findFirstChildWithDetails(className: String): ViewDetails? {
    if (this.className == className) return this

    for (child in children) {
        val found = child.findFirstChildWithDetails(className)
        if (found != null) {
            return found
        }
    }

    return null
}

private fun ViewDetails.print(to: StringBuilder? = null) {
    val tabsBuilder = StringBuilder()
    for (t in 0 until depth) {
        tabsBuilder.append("\t")
    }
    val tabs = tabsBuilder.toString()

    val head = "$tabs<$className, size = (${dimensions.width} x ${dimensions.height}), position = (${position.x} x ${position.y})>\n"

    to?.append(head) ?: SdkLogger.log(head)

    children.forEach {
        it.print(to)
    }

    val end = "$tabs</$className>\n"
    to?.append(end) ?: SdkLogger.log(end)
}

fun ActivityDetails.toJson(): JSONObject {
    val json = JSONObject()
            .put(ActivityDetails.Keys.NAME, name)
            .put(ActivityDetails.Keys.PACKAGE, packageName)
            .put(ActivityDetails.Keys.TIMESTAMP, timestamp)

    viewDetails?.toJson()?.let {
        json.put(ActivityDetails.Keys.VIEWS, it)
    }

    return json
}


private fun ViewDetails.toJson(): JSONObject {
    val viewDetailsJson = JSONObject()
            .put("className", className)
            .put("depth", depth)
            .put("position", position.toJson())
            .put("dimensions", dimensions.toJson())

    if (!isLeaf) {
        viewDetailsJson.put("children", JSONArray(children.map { it.toJson() }.toList()))
    }

    return viewDetailsJson
}
