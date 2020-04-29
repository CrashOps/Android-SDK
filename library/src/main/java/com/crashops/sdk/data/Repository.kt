package com.crashops.sdk.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import com.crashops.sdk.COHostApplication
import com.crashops.sdk.CrashOps
import com.crashops.sdk.util.Constants
import com.crashops.sdk.util.SdkLogger
import com.crashops.sdk.util.Strings
import com.crashops.sdk.util.Utils
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class Repository {

    internal val hostAppDetails = Bundle()
    private val filesHelper = FilesHelper()

    private val logsFolder: File? by lazy {
        filesHelper.filesDir?.let { folder ->
            val created: Boolean
            val logsDir = File(folder.absolutePath + "/${Strings.SDK_IDENTIFIER}_logs/")
            created = if (!logsDir.exists() || !logsDir.isDirectory) {
                logsDir.mkdirs()
            } else {
                true
            }

            if (created) {
                logsDir
            } else {
                null
            }
        } ?: run {
            SdkLogger.error(TAG, "Couldn't get to device's cache folder")
            null
        }
    }

    private val crashLogsFolder: File? by lazy {
        logsFolder?.let {
            val created: Boolean
            val crashLogsDir = File(it.absolutePath + "/crashes/")
            created = if (!crashLogsDir.exists() || !crashLogsDir.isDirectory) {
                crashLogsDir.mkdirs()
            } else {
                true
            }

            if (created) {
                crashLogsDir
            } else {
                null
            }
        } ?: run {
            SdkLogger.error(TAG, "Couldn't get to device's cache folder")
            null
        }
    }

    private val errorLogsFolder: File? by lazy {
        logsFolder?.let {
            val created: Boolean
            val errorLogsDir = File(it.absolutePath + "/errors/")
            created = if (!errorLogsDir.exists() || !errorLogsDir.isDirectory) {
                errorLogsDir.mkdirs()
            } else {
                true
            }

            if (created) {
                errorLogsDir
            } else {
                null
            }
        } ?: run {
            SdkLogger.error(TAG, "Couldn't get to device's cache folder")
            null
        }
    }

    private var _previousCrashLogs: List<String>? = null
    var previousCrashLogs: List<String>
    get() {
        val crashLogs = _previousCrashLogs
        if (crashLogs != null) {
            _previousCrashLogs = arrayListOf()
        }
        return crashLogs ?: arrayListOf()
    }
    set(value) {
        if (_previousCrashLogs == null) {
            _previousCrashLogs = value
            if (COHostApplication.shared().isInForeground()) {
                CrashOps.getInstance().onPreviousCrashLogsUpdated(previousCrashLogs)
            }
        }
    }

    init {
        try {
            val info = COHostApplication.sharedInstance().packageManager.getPackageInfo(COHostApplication.sharedInstance().packageName, 0)

            hostAppDetails.putString(Constants.Keys.HOST_APP_VERSION_NAME, info.versionName)
            hostAppDetails.putLong(Constants.Keys.HOST_APP_VERSION_CODE, info.longVersionCode)
            hostAppDetails.putString(Constants.Keys.HOST_APP_PACKAGE_NAME, info.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            SdkLogger.error(TAG, "Failed to load host app's details!", e)
        }
    }

    fun storeDeviceId(id: String) {
        writeStringToExternalFile(id, Constants.Keys.DeviceId)
    }

    fun readFileDirectlyAsText(fileName: String): String
            = File(fileName).readText(Charsets.UTF_8)

    private fun deleteExternalFile(fileName: String): Boolean {
        // TODO Is it the SD card really? https://stackoverflow.com/questions/8181242/write-to-a-file-in-sd-card-in-android
//        val externalFilesFolder = Environment.getExternalStorageDirectory() ?: return false
        val externalFilesFolder = COHostApplication.shared().getExternalFilesDir(null) ?: return false
        val dir = File(externalFilesFolder.absolutePath + "/CrashOps/")

        if(!dir.exists()) {
            if (!dir.mkdirs()) {
                return true
            }
        }

        val file = File(dir, fileName)

        return file.delete()
    }

    private fun writeStringToExternalFile(content: String, fileName: String): Boolean {
        var didWrite = false
        // TODO Is it the SD card really? https://stackoverflow.com/questions/8181242/write-to-a-file-in-sd-card-in-android
//        val externalFilesFolder = Environment.getExternalStorageDirectory() ?: return false
        val externalFilesFolder = COHostApplication.shared().getExternalFilesDir(null) ?: return false
        val dir = File(externalFilesFolder.absolutePath + "/CrashOps/")

        if(!dir.exists()) {
            if (!dir.mkdirs()) {
                return false
            }
        }

        val file = File(dir, fileName)

        try {
            val outputStream = FileOutputStream(file)
            outputStream.write(content.toByteArray())
            didWrite = true
        } catch (e: FileNotFoundException) {
            SdkLogger.error(TAG, e)
        } catch (e: IOException) {
            SdkLogger.error(TAG, e)
        }

        return didWrite
    }

    private fun readExternalFileContent(fileName: String): String? {
//        val externalFilesFolder = Environment.getExternalStorageDirectory() ?: return null
        val externalFilesFolder = COHostApplication.shared().getExternalFilesDir(null) ?: return null
        val dir = File(externalFilesFolder.absolutePath + "/CrashOps/")
        if(!dir.exists()) {
            return null
        }

        val file = File(dir, fileName)

        var content: String? = null
        try {
            content = file.readText(Charsets.UTF_8)
        } catch (e: FileNotFoundException) {
            SdkLogger.error(TAG, e)
        } catch (e: IOException) {
            SdkLogger.error(TAG, e)
        }

        return content
    }

    @SuppressLint("ApplySharedPref")
    fun storeCustomValue(key: String, value: String, atomically: Boolean = false): Boolean {
        val editor = COHostApplication.shared()
            .getSharedPreferences(Constants.Keys.GlobalPersistenceFileName, Context.MODE_PRIVATE)
            ?.edit()?.putString(key, value)

        if (atomically) {
            editor?.commit()
        } else {
            editor?.apply()
        }

        return editor != null
    }

    fun loadCustomValue(key: String, defaultValue: String? = null): String? {
        return COHostApplication.shared()
            .getSharedPreferences(Constants.Keys.GlobalPersistenceFileName, Context.MODE_PRIVATE)
            ?.getString(key, null) ?: defaultValue
    }

    fun deleteCustomValue(key: String) {
        COHostApplication.shared()
            .getSharedPreferences(Constants.Keys.GlobalPersistenceFileName, Context.MODE_PRIVATE)
            ?.edit()?.remove(key)?.apply()
    }

    fun deviceId(): String? {
        return readExternalFileContent(Constants.Keys.DeviceId)
    }

    fun storeCrashLog(log: String, time: Long? = null): String? {
        val now = time ?: Utils.now()
        val sessionId = CrashOps.getInstance().sessionId
        val filename = "android_crashed_on_${Strings.timestamp(now, "yyyy_MM_dd_HH_mm_ssZ")}_$sessionId.log"
        var didSave = false
        crashLogsFolder?.let { sdkDir ->
            val file = File(sdkDir, filename)

            try {
                val logFile = FileOutputStream(file)
                logFile.write(log.toByteArray())
                didSave = true
            } catch (e: FileNotFoundException) {
                SdkLogger.error(TAG, e)
            } catch (e: IOException) {
                SdkLogger.error(TAG, e)
            }
        }

        return if (didSave) {
            filename
        } else {
            null
        }
    }

    fun storeErrorLog(log: String, time: Long? = null): String? {
        val now = time?.let { it } ?: Utils.now()
        val sessionId = CrashOps.getInstance().sessionId
        val filename = "android_error_on__${Strings.timestamp(now, "yyyy_MM_dd_HH_mm_ssZ")}_$sessionId.log"

        var didSave = false
        errorLogsFolder?.let { sdkDir ->
            val file = File(sdkDir, filename)

            try {
                val logFile = FileOutputStream(file)
                logFile.write(log.toByteArray())
                didSave = true
            } catch (e: FileNotFoundException) {
                SdkLogger.error(TAG, e)
            } catch (e: IOException) {
                SdkLogger.error(TAG, e)
            }
        }

        return if (didSave) {
            filename
        } else {
            null
        }
    }

    fun loadLogFileContent(filename: String): String? {
        val logsFolder = logsFolder ?: return null
        val file = File(logsFolder, filename)

        return file.readText()
    }

    /// Removes all traces
    fun clearAllHistory(): Boolean {
        return logsFolder?.let { sdkDir ->
            return sdkDir.deleteRecursively()
        } ?: false
    }

    fun loadCrashLogFiles(): ArrayList<File> {
        val filesList = ArrayList<File>()
        val logs = crashLogsFolder ?: run {
            SdkLogger.error(TAG, "Couldn't get to device's cache folder")
            null
        }

        logs?.listFiles()?.forEach {
            if (it.path.endsWith("log")) {
                filesList.add(it)
            }
        }

        return filesList
    }

    fun loadErrorLogFiles(): ArrayList<File> {
        val filesList = ArrayList<File>()
        val logs = errorLogsFolder ?: run {
            SdkLogger.error(TAG, "Couldn't get to device's cache folder")
            null
        }

        logs?.listFiles()?.forEach {
            if (it.path.endsWith("log")) {
                filesList.add(it)
            }
        }

        return filesList
    }

    fun deleteDeviceId() {
        deleteExternalFile(Constants.Keys.DeviceId)
    }

    fun appMetadata(): String {
        return "<TODO>"
    }

    companion object {
        val FILES_PATH: String = ".CrashOps${File.separator}files"
        private val TAG: String = Repository::class.java.simpleName
        @JvmStatic
        val instance: Repository = Repository()

        fun isPermittedForExternalStorage(activity: Activity): Boolean {
            return instance.filesHelper.isPermittedForExternalStorage(activity)
        }
    }
}
