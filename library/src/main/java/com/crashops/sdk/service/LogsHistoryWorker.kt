package com.crashops.sdk.service

import android.app.job.JobParameters
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.Nullable
import androidx.concurrent.futures.ResolvableFuture
import androidx.work.*
import com.crashops.sdk.communication.Communicator
import com.crashops.sdk.configuration.Configurations
import com.crashops.sdk.data.Repository
import com.crashops.sdk.service.LogsHistoryWorker.Companion.TAG
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import androidx.work.WorkManager
import com.crashops.sdk.COHostApplication
import com.crashops.sdk.PreviousLogsListener
import com.crashops.sdk.util.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class LogsHistoryWorker(appContext: Context, workerParams: WorkerParameters) : ListenableWorker(appContext, workerParams) {

    private val sdkBackgroundHandler: Handler by lazy {
        val appBackgroundThread = HandlerThread(Strings.SDK_NAME + "_BackgroundThread")
        appBackgroundThread.start()
        Handler(appBackgroundThread.looper)
    }

    private var mFuture: ResolvableFuture<Result>? = null
    private var bgTask: BackgroundRunnable? = null

    companion object {
        val TAG = LogsHistoryWorker::class.java.simpleName
        private fun NAME(context: Context): String {
            return "${Strings.SDK_NAME}-${context.packageName}"
        }

        var cachedIntervalSetting: Long? = null
        var _lastServiceCall: Long? = null

        private const val _JOB_PERIOD_MILLISECONDS: Long = (Constants.ONE_MINUTE_MILLISECONDS * 20)
        private fun JOB_PERIOD_DEFAULT_MILLISECONDS(): Long {
            return when (Utils.isReleaseVersion) {
                true -> {
                    _JOB_PERIOD_MILLISECONDS
                }
                else -> {
                    Constants.ONE_HOUR_MILLISECONDS
                }
            }
        }

        fun setLastCallTimestamp(context: Context, lastCall: Long?) {
            _lastServiceCall = lastCall
            lastCall?.let {
                context
                    .getSharedPreferences(Constants.Keys.LogsPersistenceFileName, Context.MODE_PRIVATE)
                    ?.edit()?.putLong(Constants.Keys.LastServiceCall, it)?.apply()
            } ?: run {
                context
                    .getSharedPreferences(Constants.Keys.LogsPersistenceFileName, Context.MODE_PRIVATE)
                    ?.edit()?.remove(Constants.Keys.LastServiceCall)?.apply()
            }
        }

        fun getLastCallTimestamp(context: Context): Long? {
            if (_lastServiceCall != null) return _lastServiceCall

            var lastCallTimestamp: Long? = null
            val lastServiceCall = context
                .getSharedPreferences(Constants.Keys.LogsPersistenceFileName, Context.MODE_PRIVATE)
                .getLong(Constants.Keys.LastServiceCall, 0)
            if (lastServiceCall > 0) {
                lastCallTimestamp = lastServiceCall
                _lastServiceCall = lastServiceCall
            }

            return lastCallTimestamp
        }

        @JvmStatic
        fun runIfIdle(context: Context, callback: (Boolean?) -> Unit = { }) {
            if (!BackgroundRunnable.isWorking) {
                BackgroundRunnable.work(context.applicationContext) { latestLog ->
                    callback.invoke(latestLog != null)
                }
            } else {
                callback.invoke(null)
            }
        }

        fun runNow(context: Context, callback: Utils.Callback<Boolean?>) {
            BackgroundRunnable.work(context.applicationContext) { didSucceed ->
                callback.onCallback(didSucceed != null)
            }
        }

        // From: https://medium.com/google-developers/scheduling-jobs-like-a-pro-with-jobscheduler-286ef8510129
        @JvmStatic
        fun registerSelf(context: Context): Boolean {
            val constraints = Constraints.Builder()
                .setTriggerContentUpdateDelay(2, TimeUnit.HOURS)
                    .build()

            try {
                // Bypassing this:
                // java.lang.IllegalStateException: WorkManager is not initialized properly.  You have explicitly disabled WorkManagerInitializer in your manifest, have not manually called WorkManager#initialize at this point, and your Application does not implement Configuration.Provider.
                WorkManager.initialize(context, COHostApplication.shared().workManagerConfiguration)

                // And this:
                // java.lang.IllegalStateException: WorkManager is already initialized.  Did you try to initialize it manually without disabling WorkManagerInitializer? See WorkManager#initialize(Context, Configuration) or the class level Javadoc for more information.
                // Is bypassed in AndroidManifest.xml

                val periodicUploadLogsWorkRequest = PeriodicWorkRequest
                        .Builder(LogsHistoryWorker::class.java, 2, TimeUnit.HOURS)
                        .addTag(TAG)
                        .setConstraints(constraints).build()

                // Schedule and override the existing workers if any. (to prevent duplicates)
                // TODO Review: removed 'ExistingPeriodicWorkPolicy.REPLACE' after reading this: https://medium.com/androiddevelopers/workmanager-periodicity-ff35185ff006
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME(context), ExistingPeriodicWorkPolicy.KEEP, periodicUploadLogsWorkRequest)
            } catch (e: java.lang.Exception) {
                SdkLogger.error(TAG, e)
                Utils.debugDialog("Failed to schedule Logs Worker!")
            }

            return true
        }

        fun uploadHistory(callback: Utils.Callback<Boolean?>) {
            val crashLogFiles = Repository.instance.loadCrashLogFiles()
            val errorLogFiles = Repository.instance.loadErrorLogFiles()

            if (crashLogFiles.size == errorLogFiles.size && crashLogFiles.size == 0) {
                // Life's good - No logs at all, keep calm and carry on :)
                callback.onCallback(true)
            } else {
                val logsSynchronizer = Synchronizer<Boolean> { successes ->
                    val didAllSucceeded = successes.reduce { accumulator, currentBoolean ->
                        accumulator && currentBoolean
                    }

                    if (successes.size > 0 && didAllSucceeded) {
                        Utils.debugToast("all files uploaded")
                    }

                    callback.onCallback(didAllSucceeded)
                }

                if (crashLogFiles.size > 0) {
                    val crashLogsHolder = logsSynchronizer.createHolder()

                    val synchronizer = Synchronizer<String> { successes ->

                        var didAllSucceeded = true
                        successes.forEach {
                            didAllSucceeded = didAllSucceeded && it != null
                        }

                        crashLogsHolder.release(didAllSucceeded)

                        Repository.instance.previousCrashLogs = successes.mapNotNull {
                            it
                        }
                    }

                    crashLogFiles.forEach {
                        it.zipIt()?.let { zipped ->
                            val holder = synchronizer.createHolder()
                            Communicator.instance.reportViaUpload(Communicator.UploadTestServerUrl, zipped) { result ->
                                SdkLogger.log(result)
                                val logContent: String?
                                if (result != null) {
                                    logContent = it.readText()
                                    try {
                                        it.delete()
                                    } catch (exception: java.lang.Exception) {
                                        SdkLogger.error(TAG, exception)
                                    }
                                } else {
                                    logContent = null
                                }

                                holder.release(logContent)
                            }
                        } ?: run {
                            SdkLogger.error(TAG, "Failed to zip $it")
                        }
                    }
                }

                if (errorLogFiles.size > 0) {
                    val errorLogsHolder = logsSynchronizer.createHolder()

                    val synchronizer = Synchronizer<String> { successes ->
                        var didAllSucceeded = true
                        successes.forEach {
                            didAllSucceeded = didAllSucceeded && it != null
                        }

                        errorLogsHolder.release(didAllSucceeded)
                    }

                    errorLogFiles.forEach {
                        it.zipIt()?.let { zipped ->
                            val holder = synchronizer.createHolder()
                            Communicator.instance.reportViaUpload(Communicator.UploadTestServerUrl, zipped) { result ->
                                SdkLogger.log(result)
                                val logContent: String?
                                if (result != null) {
                                    logContent = it.readText()
                                    try {
                                        it.delete()
                                    } catch (exception: java.lang.Exception) {
                                        SdkLogger.error(TAG, exception)
                                    }
                                } else {
                                    logContent = null
                                }

                                holder.release(logContent)
                            }
                        } ?: run {
                            SdkLogger.error(TAG, "Failed to zip $it")
                        }
                    }
                }
            }
        }

        @JvmStatic
        fun testSelf(context: Context) {
            if (Utils.isReleaseVersion) return

            runNow(context, object : Utils.Callback<Boolean?> {
                override fun onCallback(result: Boolean?) {
                    SdkLogger.log(result)
                }
            })
        }
    }

    // Invoked automatically by the OS
    override fun startWork(): ListenableFuture<Result> {
        val future = ResolvableFuture.create<Result>()
        mFuture = future

        bgTask = BackgroundRunnable(applicationContext, mFuture)
        bgTask?.let {
            sdkBackgroundHandler.post(it)
        }
        SdkLogger.log(TAG, "Called `startWork`...")

        return future
    }

    fun onStopJob(params: JobParameters) {
        sdkBackgroundHandler.removeCallbacks(null)
        bgTask?.onResult?.invoke(null)
        mFuture?.setException(Exception("Stopped by OS"))
    }
}

private fun File.zipIt(): File? {
    return Zipper.zipIt(this)
}

private class BackgroundRunnable(private val applicationContext: Context, @Nullable private val future: ResolvableFuture<ListenableWorker.Result>?, @Nullable val onResult: ((String?) -> Unit)? = null) : Runnable {

    companion object {
        private val _isWorking: AtomicBoolean = AtomicBoolean(false)

        val isWorking: Boolean
            get() {
                return _isWorking.get()
            }

        // Invoked manually by the app / widget (1)
        fun work(appContext: Context, onResult: ((String?) -> Unit)) {
            BackgroundRunnable(appContext, null, onResult).runAsync()
        }
    }

    private var previousCrashLogs: ArrayList<File>? = null
    private var previousErrorLogs: ArrayList<File>? = null

    private val backgroundHandler: Handler by lazy {
        val handlerThread = HandlerThread("${Strings.SDK_NAME}.service")
        handlerThread.start()
        Handler(handlerThread.looper)
    }

    private fun shouldRunNow(): Boolean {
        if (!Configurations.isEnabled()) return false
        if (isWorking) return false
        if (anyLeftOversExist) return true

        return false
    }

    private val anyLeftOversExist: Boolean get() {
        if (!Configurations.isEnabled()) return false

        val crashLogs =  Repository.instance.loadCrashLogFiles()
        val errorLogs =  Repository.instance.loadErrorLogFiles()
        if (crashLogs.size == 0 && errorLogs.size == 0) return false

        val isSameCrashLogsList = (previousCrashLogs?.intersect(crashLogs)?.size ?: 0) == crashLogs.size
        val isSameErrorLogsList = (previousErrorLogs?.intersect(errorLogs)?.size ?: 0) == errorLogs.size

        previousCrashLogs = crashLogs
        previousErrorLogs = errorLogs

        return !(isSameCrashLogsList && isSameErrorLogsList)
    }

    // Invoked manually by the app / widget (2)
    private fun runAsync() {
        backgroundHandler.post(this)
    }

    override fun run() {
        SdkLogger.log(TAG, "background worker started...")

        executeUpload(false)
    }

    fun executeUpload(forced: Boolean = false) {
        SdkLogger.log(TAG, "background worker started...")

        if (shouldRunNow() || forced) {
            if (isWorking) return
            _isWorking.set(true)

            LogsHistoryWorker.uploadHistory(object : Utils.Callback<Boolean?> {
                override fun onCallback(result: Boolean?) {
                    SdkLogger.log(TAG, result)

                    _isWorking.set(false)

                    if (anyLeftOversExist) {
                        // Retry and see if there are leftovers...
                        executeUpload(true)
                    } else {
                        future?.set(ListenableWorker.Result.success())

                        onResult?.invoke(result?.toString())
                        LogsHistoryWorker.setLastCallTimestamp(applicationContext, Utils.now())
                    }
                }
            })
        } else {
            SdkLogger.log(TAG, "Periodic worker fired but it will be ignored because the app is in foreground / service is disabled by the user...")

            future?.set(ListenableWorker.Result.retry())

            onResult?.invoke(null)
            LogsHistoryWorker.setLastCallTimestamp(applicationContext, Utils.now())
        }
    }
}
