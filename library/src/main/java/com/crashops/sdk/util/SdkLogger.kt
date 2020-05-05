package com.crashops.sdk.util

import android.util.Log
import com.crashops.sdk.configuration.Configurations

class SdkLogger {

    companion object {
        @Suppress("FunctionName")
        private fun _log(reporter: String, logMessage: Any?) {
            if (Utils.isReleaseVersion) return
            if (!Configurations.isEnabled()) return

            Log.d(reporter, logMessage.toString())
        }

        fun log(reporter: Any, logMessage: Any?) {
            log(reporter.javaClass.simpleName, logMessage.toString())
        }

        fun log(reporter: Any, logMessage: String) {
            log(reporter.javaClass.simpleName, logMessage)
        }

        @JvmStatic
        fun log(logMessage: Any?) {
            log("Anonymous reporter", logMessage.toString())
        }

        fun error(reporter: Any, logMessage: Any?) {
            error(reporter.javaClass.simpleName, logMessage.toString())
        }

        fun error(reporter: String, throwable: Throwable) {
            if (Utils.isReleaseVersion) return
            if (!Configurations.isEnabled()) return

            throwable.message?.let {
                Log.e(reporter, it)
            } ?: run {
                Log.e(reporter, throwable.toString())
            }
            throwable.printStackTrace()
        }

        @JvmStatic
        fun internalError(reporter: String, logMessage: Any?) {
            if (Utils.isReleaseVersion) return
            if (!Configurations.isEnabled()) return

            logMessage?.let {
                Log.e(reporter, it.toString())
            }
        }

        @JvmStatic
        fun error(reporter: String, logMessage: Any?) {
            if (Utils.isReleaseVersion) return
            if (!Configurations.isEnabled()) return

            logMessage?.let {
                Log.e(reporter, it.toString())
                Utils.debugToast(it.toString())
            }
        }

        @JvmStatic
        fun log(tag: String, msg: String) {
            _log(tag, msg)
        }

        @JvmStatic
        fun error(tag: String, errorMessage: String, throwable: Throwable) {
            if (Utils.isReleaseVersion) return
            if (!Configurations.isEnabled()) return

            Log.e(tag, errorMessage)
            error(tag, throwable)
        }
    }
}