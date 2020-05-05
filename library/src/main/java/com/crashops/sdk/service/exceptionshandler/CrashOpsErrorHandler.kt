package com.crashops.sdk.service.exceptionshandler

import android.os.Bundle
import com.crashops.sdk.COHostApplication
import com.crashops.sdk.OnCrashListener
import com.crashops.sdk.CrashOps
import com.crashops.sdk.configuration.Configurations
import com.crashops.sdk.data.Repository
import com.crashops.sdk.data.model.ActivityDetails
import com.crashops.sdk.data.model.Position
import com.crashops.sdk.data.model.Size
import com.crashops.sdk.data.model.ViewDetails
import com.crashops.sdk.service.LogsHistoryWorker
import com.crashops.sdk.util.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.Serializable
import java.io.StringWriter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList

/**
 * Created by perrchick on 15/01/2020.
 */
class CrashOpsErrorHandler private constructor() : Thread.UncaughtExceptionHandler {
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

    private var rootHandler: Thread.UncaughtExceptionHandler? = null
    set(value) {
        if (value != instance) {
            field = value
        } else {
            SdkLogger.error(TAG, "ignored assignment of rootHandler because it's already assigned to ${Strings.SDK_NAME} ($instance)")
        }
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
                val crashLog = LogGenerator.generateLog(thread, throwable, Bundle().withBoolean(Constants.Keys.Json.IS_FATAL, true), time)
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
                rootHandler = Thread.getDefaultUncaughtExceptionHandler()
                // This catches all threads' exceptions, unlike: `CrashOpsErrorHandler.Companion.getInstance().setRootHandler(Thread.currentThread().getUncaughtExceptionHandler());`
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
        val extra = Bundle()
                .withString(Constants.Keys.Json.ERROR_TITLE, title)
                .withInnerBundle(Constants.Keys.Json.ERROR_DETAILS, errorDetails)

        val crashLog = LogGenerator.generateLog(Thread.currentThread(), errorThrowable, extra, time)
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
        fun generateLog(originThread: Thread, throwable: Throwable, extra: Bundle? = null, time: Long? = null): String {
            val allStackTraces = Thread.getAllStackTraces().entries

            val screenTraces = Repository.instance.tracer?.breadcrumbsReport()?.map {
                it.toJson()
            }

            val logJsonObject = JSONObject()
            
            logJsonObject.put(Constants.Keys.Json.ORIGIN, throwable.toJson())

            extra?.let { moreInfo ->
                moreInfo.keySet().forEach { key ->
                    (moreInfo.get(key) as? Bundle)?.let {
                        logJsonObject.put(key, JSONObject(it.toMap()))
                    } ?: run {
                        logJsonObject.put(key, moreInfo.get(key))
                    }
                }
            }

            val now = time ?: Utils.now()

            if (Utils.isDebugMode) {
                logJsonObject.put(Constants.Keys.Json.DEBUG_ID, UUID.randomUUID().toString())
            }

            val buildModeString = if (COHostApplication.shared().isHostAppDebuggable) {
                Constants.DEBUG
            } else {
                Constants.RELEASE
            }
            logJsonObject.put(Constants.Keys.Json.BUILD_MODE, buildModeString)

            logJsonObject.put(Constants.Keys.Json.DEVICE_PLATFORM, Constants.Keys.Json.DEVICE_PLATFORM_ANDROID)

            logJsonObject.put(Constants.Keys.Json.TIMESTAMP, now)
            logJsonObject.put(Constants.Keys.Json.LOCAL_TIME, Strings.timestamp(now,"yyyy_MM_dd_HH_mm_ssZ"))
            logJsonObject.put(Constants.Keys.Json.HOST_APP_DETAILS, JSONObject(Repository.instance.hostAppDetails.toMap()))

            logJsonObject.put(Constants.Keys.Json.SESSION_ID, CrashOps.getInstance().sessionId)
            var deviceInfo = CrashOps.getInstance().deviceInfo
            if (deviceInfo.isEmpty()) {
                deviceInfo = DeviceInfoFetcher.getDeviceInfo()
            }

            logJsonObject.put(Constants.Keys.Json.DEVICE_INFO, JSONObject(deviceInfo))
            logJsonObject.put(Constants.Keys.Json.METADATA, JSONObject(CrashOps.getInstance().appMetadata().toMap()))
            logJsonObject.put(Constants.Keys.Json.ORIGIN_THREAD, "${originThread.name} (${originThread.id})")

            logJsonObject.put(Constants.Keys.Json.DID_EXPORT_WIREFRAMES, Configurations.shouldExportWireframes())
            screenTraces?.let {
                logJsonObject.put(Constants.Keys.Json.SCREEN_TRACES, JSONArray(it))
            }

            val currentThreadId = Thread.currentThread().id
            val stackTraces: ArrayList<JSONObject> = arrayListOf()
            allStackTraces.forEach { stackTraceEntry ->
                val stackTraceEntries = stackTraceEntry.value.toList()
                val traces: List<String> = stackTraceEntries.map { traceElement ->
                    traceElement.toString()
                }

                if (currentThreadId == stackTraceEntry.key.id) {
                    // i.e. `continue` (skip the crashed stack trace because it already appears)
                    return@forEach
                }

                stackTraces.add(JSONObject()
                        .put(Constants.Keys.Json.STACK_TRACE, JSONArray(traces))
                        .put("name", "${stackTraceEntry.key.name} (${stackTraceEntry.key.id})"))
            }

            logJsonObject.put(Constants.Keys.Json.OTHER_PROCESSES, JSONArray(stackTraces))

            return logJsonObject.toString()
        }
    }
}

private fun Throwable.toJson() : JSONObject {
    val throwableJson = JSONObject()

    val stackTraceString = StringWriter()
    printStackTrace(PrintWriter(stackTraceString))
    val stackTraceStrings = stackTraceString.toString()
            .split("\n")
            .filterNot { it.isEmpty() }
            .map { it.replace("\t", "") }
            .map { it.replace("at ", "") }

    val stackTrace = stackTraceStrings.subList(1, stackTraceStrings.size)

    (this as? ThrowableWithExtra)?.extra?.let { throwableExtraInfo ->
        throwableExtraInfo.keySet().forEach { key ->
            (throwableExtraInfo.get(key) as? Bundle)?.let {
                throwableJson.put(key, JSONObject(it.toMap()))
            } ?: run {
                throwableJson.put(key, throwableExtraInfo.get(key))
            }
        }
    }

    throwableJson.put(Constants.Keys.Json.MESSAGE_TITLE, toString())
    throwableJson.put(Constants.Keys.Json.STACK_TRACE, JSONArray(stackTrace))

    cause?.let {
        // origin cause
        throwableJson.put(Constants.Keys.Json.CAUSE, it.toJson())
    }

    return throwableJson
}

private fun ActivityDetails.toJson(): JSONObject {
    return JSONObject()
            .put("name", name)
            .put("package", packageName)
            .put("timestamp", timestamp)
            .put("views", viewDetails().toJson())
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

private fun Size.toJson(): JSONObject {
    return JSONObject()
            .put("width", width)
            .put("height", height)
}

private fun Position.toJson(): JSONObject {
    return JSONObject()
            .put("x", x.toInt())
            .put("y", y.toInt())
}

class ThrowableWithExtra: Throwable() {
    var extra: Bundle? = null
}