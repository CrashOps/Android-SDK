package com.crashops.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import androidx.work.Configuration
import com.crashops.sdk.data.Repository
import com.crashops.sdk.service.LogsHistoryWorker
import com.crashops.sdk.util.AppLifecycleTracker
import com.crashops.sdk.util.PrivateEventBus
import com.crashops.sdk.util.SdkLogger

class COHostApplication(base: Context?) : ContextWrapper(base), Configuration.Provider {
    companion object {
        private var _shared: COHostApplication? = null
        fun shared(): COHostApplication {
            return _shared!!
        }

        @JvmStatic
        fun sharedInstance(): COHostApplication {
            return shared()
        }

        @JvmStatic
        fun setContext(context: Context) {
            if (_shared == null) {
                synchronized(COHostApplication) {
                    if (_shared == null) {
                        _shared = COHostApplication(context.applicationContext)
                        _shared?.init()
                    }
                }
            }
        }
    }

    private lateinit var applicationStateObserver: PrivateEventBus.Receiver
    private val activitiesTracker: AppLifecycleTracker = AppLifecycleTracker()
    private val TAG: String = COHostApplication::class.simpleName.toString()
    private lateinit var appBackgroundHandler: Handler
    private lateinit var mainThreadHandler: Handler
    val isReleaseVersion: Boolean = !BuildConfig.DEBUG

    private fun init() {
        val appBackgroundThread = HandlerThread(COHostApplication::class.java.simpleName + "_BackgroundThread")
        appBackgroundThread.start()
        appBackgroundHandler = Handler(appBackgroundThread.looper)

        mainThreadHandler = Handler()

        applicationStateObserver = PrivateEventBus.createNewReceiver(object : PrivateEventBus.BroadcastReceiverListener {
            override fun onBroadcastReceived(intent: Intent, receiver: PrivateEventBus.Receiver) {
                if (intent.action == PrivateEventBus.Action.APPLICATION_GOING_FOREGROUND) {
                    onApplicationForeground()
                }
                if (intent.action == PrivateEventBus.Action.APPLICATION_GOING_BACKGROUND) {
                    onApplicationBackground()
                }
            }
        }, PrivateEventBus.Action.APPLICATION_GOING_BACKGROUND, PrivateEventBus.Action.APPLICATION_GOING_FOREGROUND)

        // From: https://stackoverflow.com/questions/4414171/how-to-detect-when-an-android-app-goes-to-the-background-and-come-back-to-the-fo
        (baseContext as? Application)?.registerActivityLifecycleCallbacks(activitiesTracker)
    }

    private fun onApplicationBackground() {
        SdkLogger.log(TAG, "application enters background")
    }

    private fun onApplicationForeground() {
        SdkLogger.log(TAG, "application enters foreground")
        LogsHistoryWorker.runIfIdle(this)

        val previousCrashLogs = Repository.instance.previousCrashLogs
        if (previousCrashLogs.isNotEmpty()) {
            CrashOps.getInstance().onPreviousCrashLogsUpdated(previousCrashLogs)
        }
        //LogsHistoryWorker.testSelf(this)
    }

    fun cleanup() {
        PrivateEventBus.cleanup()
        (baseContext as? Application)?.unregisterActivityLifecycleCallbacks(activitiesTracker)
    }

    fun runInBackgroundThread(closure: () -> Unit) {
        runInBackgroundThread(Runnable { closure() })
    }

    fun runInBackgroundThread(bgTask: Runnable) {
        runInBackgroundThread(bgTask, 0)
    }

    fun runInBackgroundThread(bgTask: Runnable, afterDelay: Long) {
        if (afterDelay > 0) {
            shared().appBackgroundHandler.postDelayed(bgTask, afterDelay)
        } else {
            shared().appBackgroundHandler.post(bgTask)
        }
    }

    fun cancelBackgroundThread(bgTask: Runnable) {
        shared().appBackgroundHandler.removeCallbacks(bgTask)
    }

    fun runOnUiThread(runnable: Runnable) {
        runOnUiThread(runnable, 0L)
    }

    fun runOnUiThread(runnable: Runnable, delay: Int?) {
        runOnUiThread(runnable, delay?.toLong())
    }

    /// All kinds of `runOnUiThread` method will eventually run this!
    fun runOnUiThread(runnable: Runnable, delay: Long?) {
        if (delay == null || delay < 1) {
            mainThreadHandler.post(runnable)
        } else {
            mainThreadHandler.postDelayed(runnable, delay)
        }
    }

    fun runOnUiThread(closure: () -> Unit) {
        runOnUiThread(Runnable { closure() })
    }

    fun runOnUiThread(closure: () -> Unit, delay: Long) {
        runOnUiThread(Runnable {
            closure()
        }, delay)
    }

    fun topActivity(): Activity? {
        return activitiesTracker.topActivity()
    }

    fun isInForeground(): Boolean {
        return activitiesTracker.isApplicationInForeground()
    }

    //region Configuration.Provider
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder().build()
    }
    //endregion
}
