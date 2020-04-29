package com.crashops.sdk.service.exceptionshandler

import android.os.Bundle
import com.crashops.sdk.COHostApplication
import com.crashops.sdk.OnCrashListener
import com.crashops.sdk.CrashOps
import com.crashops.sdk.configuration.Configurations
import com.crashops.sdk.data.Repository
import com.crashops.sdk.service.LogsHistoryWorker
import com.crashops.sdk.util.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.Serializable
import java.io.StringWriter
import java.lang.Thread.UncaughtExceptionHandler
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList

/**
 * Created by perrchick on 15/01/2020.
 */
class CrashOpsErrorHandler private constructor() : UncaughtExceptionHandler {
    companion object {
        @JvmStatic
        val instance: CrashOpsErrorHandler = CrashOpsErrorHandler()

        // Learned it from `CrashlyticsUncaughtExceptionHandler` and `CrashlyticsController`
        private val isHandlingException: AtomicBoolean = AtomicBoolean()
        private val TAG = CrashOpsErrorHandler::class.simpleName.toString()
        private val CRASH_IN_LOOP = (5 * 1000).toLong() //5 seconds
        private var lastCrashTime: Long = 0

        // converting the StackTrace to a string
        private fun exceptionStacktraceToString(e: Throwable): String {
            return Arrays.toString(e.stackTrace)
        }
    }

    private var onCrashListener: OnCrashListener? = null

    var rootHandler: UncaughtExceptionHandler? = null
    set(value) {
        if (value != instance) {
            field = value
        } //else {
//            SdkLogger.log(TAG, "ignored assignment")
//        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        onCrash(thread, throwable)
    }

    private fun onCrash(thread: Thread, throwable: Throwable) {
        isHandlingException.set(true)

        if (Configurations.isEnabled()) {
            if (isExceptionFromCrashOps(throwable)) {
                // The Exception was thrown from the SDK
                try {
                    if (System.currentTimeMillis() - lastCrashTime < CRASH_IN_LOOP) {
                        SdkLogger.error(TAG + ": LOOP! " + thread.name, throwable)
                        return
                    }

                    lastCrashTime = System.currentTimeMillis()
                    SdkLogger.error(TAG + " in thread '" + thread.name + "'", throwable)
                    // restart();
                } catch (e: Throwable) {
                    SdkLogger.error(TAG, e)
                }

                Utils.debugDialog("SDK Exception: $throwable")
            }

            SdkLogger.log(TAG, "Exception caught by CrashOps! Details:")
            SdkLogger.error(TAG, throwable)

            try {
                val time = Utils.now()
                val crashLog = LogGenerator.generateLog(throwable, Bundle().withBoolean(Constants.Keys.Json.IS_FATAL, true), time)
                Repository.instance.storeCrashLog(crashLog, time)
            } catch (e: Throwable) {
                // Crashed while to generated crash log file
                SdkLogger.error(TAG, e)
            }
        }

        reportToHostApp(throwable)
        rootHandler?.uncaughtException(thread, throwable)

        isHandlingException.set(false)
    }

    private fun reportToHostApp(throwable: Throwable) {
        onCrashListener?.onCrash(throwable)

        if (!Configurations.isEnabled() && onCrashListener != null) {
            // it shouldn't have a listener if the SDK is disables
            SdkLogger.error(TAG, "`onCrashListener` shouldn't exist")
        }

        PrivateEventBus.notify(CrashOps.Action.CRASH_OCCURRED,
                extraValues = Bundle()
                        .withSerializable(CrashOps.ExtraKeys.THROWABLE, throwable))
    }

    fun setOnCrashListener(onCrashListener: OnCrashListener?) {
        this.onCrashListener = onCrashListener
    }


    // checking if the exception was thrown from our sdk. returns True if StackTrace contains sdk's package inside.
    private fun isExceptionFromCrashOps(ex: Throwable): Boolean {
        if (ex.message == Strings.TestedExceptionName) return false

        var isInnerExceptionFromSDK = false
        val innerThrowable = ex.cause
        if (innerThrowable != null) {
            // Check recursively
            isInnerExceptionFromSDK = isExceptionFromCrashOps(innerThrowable)
        }

        val stacktraceString: String = exceptionStacktraceToString(ex)
        val isFromSDK = stacktraceString.contains("com.crashops.sdk")
        val isIntended = stacktraceString.contains("com.crashops.sdk.CrashOps.crash")

        return (isFromSDK && !isIntended) || isInnerExceptionFromSDK
    }

    fun revert() {
        if (rootHandler != null) {
            Thread.setDefaultUncaughtExceptionHandler(rootHandler)
        }

        instance.setOnCrashListener(null)
    }

    fun initiate() {
        if (!Configurations.isEnabled()) return

        if (rootHandler == null || Thread.getDefaultUncaughtExceptionHandler() != this) {
            takeOverExceptions()
        }
    }

    private fun takeOverExceptions() {
        when (rootHandler) {
            null -> {
                // Much better than: CrashOpsErrorHandler.Companion.getInstance().setRootHandler(Thread.currentThread().getUncaughtExceptionHandler());
                rootHandler = Thread.getDefaultUncaughtExceptionHandler()
                // Much better than: Thread.currentThread().setUncaughtExceptionHandler(CrashOpsErrorHandler.Companion.getInstance());
                Thread.setDefaultUncaughtExceptionHandler(instance)
            }
            this -> SdkLogger.error(TAG, "OMG! Someone called `initiate` twice???")
            else -> SdkLogger.error(TAG, "Did someone called `initiate` twice???")
        }
    }

    fun onError(title: String, errorDetails: Bundle, errorStackTrace: Array<StackTraceElement>) {
        val time = Utils.now()

        val errorThrowable = ThrowableWithExtra()
        errorThrowable.stackTrace = errorStackTrace
        errorThrowable.extra = Bundle().withBoolean(Constants.Keys.Json.IS_FATAL, false)
        val extra = Bundle().withString(Constants.Keys.Json.ERROR_TITLE, title).withInnerBundle(Constants.Keys.Json.ERROR_DETAILS, errorDetails)

        val crashLog = LogGenerator.generateLog(errorThrowable, extra, time)
        Repository.instance.storeErrorLog(crashLog)
        LogsHistoryWorker.runNow(COHostApplication.shared(), callback = object: Utils.Callback<Boolean?> {
            override fun onCallback(result: Boolean?) {
                // did finish...
            }
        })
    }

}

private fun Bundle.withInnerBundle(key: String, bundleValue: Bundle): Bundle {
    putBundle(key, bundleValue)
    return this
}

private fun Bundle.withBoolean(keyString: String, boolValue: Boolean): Bundle {
    putBoolean(keyString, boolValue)
    return this
}

private fun Bundle.withLong(keyString: String, longValue: Long): Bundle {
    putLong(keyString, longValue)
    return this
}

private fun Bundle.withString(keyString: String, stringValue: String): Bundle {
    putString(keyString, stringValue)
    return this
}

private fun Bundle.withSerializable(keySerializable: String, serializableValue: Serializable): Bundle {
    putSerializable(keySerializable, serializableValue)
    return this
}

private fun Bundle.toMap(): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf()

    this.keySet().forEach {
        this.get(it)?.let { value ->
            if (value is Bundle) {
                map.put(it, value.toMap())
            } else {
                map.put(it, value)
            }
        }
    }

    return map
}

class LogGenerator {
    companion object {
        fun generateLog(throwable: Throwable, extra: Bundle? = null, time: Long? = null): String {
            val allStackTraces = Thread.getAllStackTraces().entries

            val stackTraceString = StringWriter()
            throwable.printStackTrace(PrintWriter(stackTraceString))
            val stackTraceStrings = stackTraceString.toString()
                    .split("\n")
                    .filterNot { it.isEmpty() }
                    .map { it.replace("\t", "") }
                    .map { it.replace("at ", "") }

            val stackTrace = stackTraceStrings.subList(1, stackTraceStrings.size)

            SdkLogger.log(stackTrace)

            val currentThread = Thread.currentThread()

            // TODO Include in each report: developer's metadata, deep link to this report on the web.
            val logJsonObject = JSONObject()

            (throwable as? ThrowableWithExtra)?.extra?.let { throwableExtraInfo ->
                throwableExtraInfo.keySet().forEach { key ->
                    (throwableExtraInfo.get(key) as? Bundle)?.let {
                        logJsonObject.put(key, JSONObject(it.toMap()))
                    } ?: run {
                        logJsonObject.put(key, throwableExtraInfo.get(key))
                    }
                }
            }

            extra?.let { moreInfo ->
                moreInfo.keySet().forEach { key ->
                    (moreInfo.get(key) as? Bundle)?.let {
                        logJsonObject.put(key, JSONObject(it.toMap()))
                    } ?: run {
                        logJsonObject.put(key, moreInfo.get(key))
                    }
                }
            }

            val now = time?.let { it } ?: Utils.now()

            val reportId = "${now}_${CrashOps.getInstance().sessionId}"
            logJsonObject.put(Constants.Keys.Json.ID, reportId)
            logJsonObject.put(Constants.Keys.Json.TIMESTAMP, now)
            logJsonObject.put(Constants.Keys.Json.LOCAL_TIME, Strings.timestamp(now,"yyyy_MM_dd_HH_mm_ssZ"))
            throwable.message?.let {
                logJsonObject.put(Constants.Keys.Json.CRASH_MESSAGE, it)
            }
            throwable.cause?.let {
                logJsonObject.put(Constants.Keys.Json.CAUSE, it)
            }

            logJsonObject.put(Constants.Keys.Json.HOST_APP_DETAILS, JSONObject(Repository.instance.hostAppDetails.toMap()))

            logJsonObject.put(Constants.Keys.Json.SESSION_ID, CrashOps.getInstance().sessionId)
            logJsonObject.put(Constants.Keys.Json.DEVICE_INFO, JSONObject(DeviceInfoFetcher.getDeviceDetails()))
            logJsonObject.put(Constants.Keys.Json.METADATA, JSONObject(CrashOps.getInstance().appMetadata().toMap()))
            logJsonObject.put(Constants.Keys.Json.STACK_TRACE, JSONArray(stackTrace))
            logJsonObject.put(Constants.Keys.Json.REPORTED_THREAD, "${currentThread.name} (${currentThread.id})")

            var crashedString = Strings.EMPTY // Saves redundant allocations
            val stackTraces: ArrayList<JSONObject> = arrayListOf()
            allStackTraces.forEach { stackTraceEntry ->
                val innerStackTrace: ArrayList<String> = arrayListOf()
                stackTraceEntry.value.forEach { e ->
                    innerStackTrace.add(e.toString())
                }

                if (currentThread == stackTraceEntry.key) {
                    return@forEach
                }

                stackTraces.add(JSONObject()
                        .put("stackTrace", JSONArray(innerStackTrace))
                        .put("name", "${stackTraceEntry.key.name} $crashedString(${stackTraceEntry.key.id})")
                )
                crashedString = Strings.EMPTY
            }

            logJsonObject.put(Constants.Keys.Json.OTHER_PROCESSES, JSONArray(stackTraces))

            return logJsonObject.toString()
        }
    }
}

class ThrowableWithExtra: Throwable() {
    var extra: Bundle? = null
}