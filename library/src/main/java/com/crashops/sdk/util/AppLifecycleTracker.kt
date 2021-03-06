package com.crashops.sdk.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.crashops.sdk.COHostApplication
import com.crashops.sdk.communication.Communicator
import java.lang.ref.WeakReference

class AppLifecycleTracker(private var lifecycleListener: LifecycleListener?) : Application.ActivityLifecycleCallbacks {
    private val TAG = AppLifecycleTracker::class.java.simpleName
    private var numStarted = 0

    private var topActivity: WeakReference<Activity>? = null
    private var isApplicationInForeground: Boolean = false

    //region Application.ActivityLifecycleCallbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
        topActivity = WeakReference(activity)

        if (numStarted++ == 0) {
            isApplicationInForeground = true
            SdkLogger.log(TAG, "onActivityStopped: app went to foreground")
            PrivateEventBus.notify(PrivateEventBus.Action.APPLICATION_GOING_FOREGROUND)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        topActivity = WeakReference(activity)
        lifecycleListener?.onActivityResumed(activity)
    }

    override fun onActivityPaused(activity: Activity) {

    }

    override fun onActivityStopped(activity: Activity) {
        if (--numStarted == 0) {
            isApplicationInForeground = false
            SdkLogger.log(TAG, "onActivityStopped: app went to background")
            PrivateEventBus.notify(PrivateEventBus.Action.APPLICATION_GOING_BACKGROUND)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {
        //
    }

    override fun onActivityDestroyed(activity: Activity) {
        Communicator.removeCallbacks(activity)
        lifecycleListener?.onActivityDestroyed(activity)
    }
    //endregion

    fun topActivity(): Activity? {
        return if (isApplicationInForeground) {
            topActivity?.get()
        } else {
            null
        }
    }

    fun isApplicationInForeground(): Boolean {
        return isApplicationInForeground
    }

    fun removeListener() {
        lifecycleListener = null
    }

}

interface LifecycleListener {
    fun onActivityResumed(activity: Activity)
    fun onActivityDestroyed(activity: Activity)
}
