package com.crashops.sdk.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.crashops.sdk.BuildConfig
import com.crashops.sdk.COHostApplication
import com.crashops.sdk.configuration.Configurations
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.*
import kotlin.math.max

class Utils {
    interface Callback<T> {
        fun onCallback(result: T)
    }

    companion object {
        val TAG = Utils::class.java.simpleName
        private val instance = Utils() // rename to: AdvertisementsManager

        @JvmStatic
        val isReleaseVersion = !BuildConfig.DEBUG
        @JvmStatic
        val isDebugMode = BuildConfig.DEBUG
        fun isRunningOnSimulator(): Boolean {
            return instance.isRunningOnSimulator
        }

        /**
         * Runs only if the SDK is in debug mode (will never operate in production and never in SDK release versions).
         */
        @JvmStatic
        fun runTests() {
            if (!isDebugMode) return

            val context = COHostApplication.shared()
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            debugToast(pInfo.toString())
        }

        fun percentage(ofValue: Int, fromValue: Int): Double {
            return if (ofValue == 0 || fromValue == 0) 0.0 else (ofValue.toDouble() / fromValue.toDouble() * 100).toDouble()
        }

        fun valueOf(percentage: Int, fromValue: Int): Double {
            val _percentage = max(0, percentage)
            return (fromValue.toDouble() * _percentage.toDouble() / 100)
        }

        fun now(shouldConsiderWorldClock: Boolean = false): Long {
            return instance.now(shouldConsiderWorldClock)
        }

        fun random(a: Int, b: Int): Int {
            if (a == b) return b

            val _min: Int = Math.min(a, b)
            val _max: Int = Math.max(a, b)
            val diff: Int = _max - _min

            return Random().nextInt(diff) + _min
        }

        fun getCurrentTimestamp(): Long {
            return now()
        }

        @JvmStatic
        /**
         * Will work only if SDK is configured as "allowed to toast"
         */
        fun debugToast(toastMessage: String) {
            if (!Configurations.isAllowedToToast()) return

            toast("[${Strings.SDK_NAME} DEBUG] $toastMessage")
        }

        fun toast(toastMessage: String) {
            COHostApplication.shared().topActivity()?.let {
                if (isRunningOnMainThread()) {
                    Toast.makeText(it, toastMessage, Toast.LENGTH_LONG).show()
                } else {
                    it.runOnUiThread {
                        toast(toastMessage)
                    }
                }
            } ?: run {
                if (isDebugMode) {
                    Toast.makeText(COHostApplication.shared(), toastMessage, Toast.LENGTH_LONG).show()
                }
            }
        }

        fun copyToClipboard(textToCopy: String): Boolean {
            val clipboard: ClipboardManager? =
                    COHostApplication.shared().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
            val clip = ClipData.newPlainText("copied by CrashOps", textToCopy)
            clipboard?.setPrimaryClip(clip)

            return textFromClipboard() == textToCopy
        }

        fun textFromClipboard(): String? {
            val clipboard: ClipboardManager? =
                COHostApplication.shared().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?

            val itemsCount = clipboard?.primaryClip?.itemCount ?: 0
            val copiedText: String? = if (itemsCount == 0) {
                null
            } else {
                clipboard?.primaryClip?.getItemAt(0)?.text.toString()
            }

            return copiedText
        }

        fun isRunningOnMainThread(): Boolean {
            return Looper.myLooper() == Looper.getMainLooper()
        }

        @JvmStatic
        /**
         * Will work only if SDK is configured as "allowed to toast"
         */
        fun debugDialog(msg: String) {
            if (!Configurations.isAllowedToAlert()) return

            showDialog(msg)
        }

        fun showDialog(msg: String) {
            COHostApplication.shared().topActivity()?.let {
                if (isRunningOnMainThread()) {
                    AlertDialog.Builder(it).setTitle("${Strings.SDK_NAME} debug").setMessage(msg).show()
                } else {
                    it.runOnUiThread {
                        debugDialog(msg)
                    }
                }
            }
        }

        fun generateUuid(): String {
            return UUID.randomUUID().toString()
        }

        fun isAdbModeEnabled(context: Context? = null): Boolean? {
            val applicationContext: Context = if (context == null) {
                COHostApplication.shared()
            } else {
                context.applicationContext
            }

            var isEnabled: Boolean? = null
            //Settings.Global.putInt(this.getContentResolver(), Settings.Global.ADB_ENABLED, 1);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                isEnabled = Settings.Global.getInt(
                    applicationContext.contentResolver,
                    Settings.Global.ADB_ENABLED,
                    0) == 1
            } else {
                // API 16 and lower...
                showDialog("Cannot check developer mode in this Android device (it's too old)")
            }

            return isEnabled
        }

        fun isDeveloperModeEnabled(context: Context? = null): Boolean? {
            val applicationContext: Context = if (context == null) {
                COHostApplication.shared()
            } else {
                context.applicationContext
            }

            var isEnabled: Boolean? = null
            //Settings.Global.putInt(this.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                isEnabled = Settings.Global.getInt(
                    applicationContext.contentResolver,
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0) == 1
            } else {
                // API 16 and lower...
                showDialog("Cannot check developer mode in this Android device (it's too old)")
            }

            return isEnabled
        }

        fun setAdbModeEnabled(context: Context? = null, isEnabled: Boolean) {
            val applicationContext: Context = if (context == null) {
                COHostApplication.shared()
            } else {
                context.applicationContext
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Settings.Global.putInt(
                    applicationContext.contentResolver,
                    Settings.Global.ADB_ENABLED,
                    if (isEnabled) {
                        1
                    } else {
                        0
                    })
            } else {
                // API 16 and lower...
                showDialog("Cannot check developer mode in this Android device (it's too old)")
            }
        }

        fun setDeveloperModeEnabled(context: Context? = null, isEnabled: Boolean) {
            val applicationContext: Context = if (context == null) {
                COHostApplication.shared()
            } else {
                context.applicationContext
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Settings.Global.putInt(
                    applicationContext.contentResolver,
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    if (isEnabled) {
                        1
                    } else {
                        0
                    })
            } else {
                // API 16 and lower...
                showDialog("Cannot check developer mode in this Android device (it's too old)")
            }
        }

        /**
         * Checks if the device is rooted.
         *
         * From: https://stackoverflow.com/questions/3424195/determining-if-an-android-device-is-rooted-programmatically
         * And from: https://stackoverflow.com/questions/1101380/determine-if-running-on-a-rooted-device
         *
         * @return `true` if the device is rooted, `false` otherwise.
         */
        fun isDeviceRooted(): Boolean {
            // get from build info
            val buildTags = Build.TAGS
            if (buildTags != null && buildTags.contains("test-keys")) {
                return true
            }

            // check if /system/app/Superuser.apk is present
            try {
                val file = File("/system/app/Superuser.apk")
                if (file.exists()) {
                    return true
                }
            } catch (e1: Exception) {
                // ignore
            }

            val isRooted = isRooted_Method1() || isRooted_Method2() || isRooted_Method3()

            // try executing commands
            return isRooted || (canExecuteCommand("/system/xbin/which su")
                    || canExecuteCommand("/system/bin/which su") || canExecuteCommand("which su"))
        }

        // executes a command on the system
        private fun canExecuteCommand(command: String): Boolean {
            return try {
                Runtime.getRuntime().exec(command)
                true
            } catch (e: Exception) {
                false
            }
        }

        private fun isRooted_Method1(): Boolean {
            val buildTags = android.os.Build.TAGS
            return buildTags != null && buildTags.contains("test-keys")
        }

        private fun isRooted_Method2(): Boolean {
            val paths = arrayOf("/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su")
            for (path in paths) {
                if (File(path).exists()) return true
            }
            return false
        }

        private fun isRooted_Method3(): Boolean {
            var process: Process? = null
            try {
                process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
                val `in` = BufferedReader(InputStreamReader(process!!.inputStream))
                if (`in`.readLine() != null) {
                    return true
                }
            } catch (t: Throwable) {
                // t.printStackTrace(); // No need rtp print
            } finally {
                process?.destroy()
            }
            return false
        }
    }

    private var timeDiff = 0

    fun setTimeDiff(diff: Int) {
        timeDiff = diff
    }

    private fun now(shouldConsiderDiff: Boolean = true): Long {
        val _timeDiff = when (shouldConsiderDiff) {
            true -> {
                timeDiff
            }
            else -> {
                0
            }
        }

        return System.currentTimeMillis() + _timeDiff
    }

    private val isRunningOnSimulator: Boolean = !isReleaseVersion &&
                (Build.FINGERPRINT.contains("generic") ||
                Build.DISPLAY.startsWith("sdk_google_phone_")
                || Build.FINGERPRINT.contains("google/sdk"))
}

fun <T> Array<T>.safeIndex(index: Int?): T? {
    if (index == null) return null
    if (index < 0) return null
    if (index >= size) return null

    return this[index]
}

fun <T> Collection<T>.safeLastOrNull(): T? {
    if (size == 0) return null

    return this.last()
}

fun File.deleteFolderExcluding(excludedFileExtension: String): Boolean = walkBottomUp().fold(true, { res, it ->
    if (it.path.endsWith(excludedFileExtension)) return@fold true
    if (it.isDirectory) return@fold true

    (it.delete() || !it.exists()) && res
})
