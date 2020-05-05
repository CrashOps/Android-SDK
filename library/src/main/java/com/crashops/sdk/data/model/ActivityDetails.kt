package com.crashops.sdk.data.model

import android.app.Activity
import android.view.View
import com.crashops.sdk.COHostApplication
import com.crashops.sdk.util.SdkLogger
import com.crashops.sdk.util.Utils

class ActivityDetails(activity: Activity) : View.OnLayoutChangeListener {
    companion object {
        private val TAG: String = ActivityDetails::class.java.simpleName
    }

    private var _viewDetails: ViewDetails
    val name: String = activity.localClassName
    fun viewDetails(): ViewDetails {
        return _viewDetails
    }
    val packageName: String = activity.packageName
    val timestamp: Long = Utils.getCurrentTimestamp()

    init {
        SdkLogger.log(TAG, Utils.getCurrentTimestamp() - timestamp)

        // These are only the initial details, we should refresh it when sizes will be available
        this._viewDetails = ViewDetails.extract(activity.window.decorView)
        val stringBuilder = java.lang.StringBuilder()
        _viewDetails.print(stringBuilder)

        activity.window.decorView.addOnLayoutChangeListener(this)
        SdkLogger.log(TAG, "\n${stringBuilder}")
    }

    override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        v?.let { changedView ->
            // Copying the tree to simpler classes must occur on the Main Thread to avoid changes while running.
            val viewDetails = ViewDetails.extract(changedView)
            COHostApplication.shared().runInBackgroundThread {
                // This comparision won't run the Main Thread to avoid janky UI
                // as it compares both trees.
                if (viewDetails == _viewDetails) {
                    // No more changes - stop observing changes
                    // (even animations will change layout so it's not recommended to endlessly observe these changes)
                    COHostApplication.shared().runOnUiThread {
                        changedView.removeOnLayoutChangeListener(this)
                    }
                } else {
                    _viewDetails = viewDetails
                }
            }
        } ?: run {
            v?.removeOnLayoutChangeListener(this)
        }
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
