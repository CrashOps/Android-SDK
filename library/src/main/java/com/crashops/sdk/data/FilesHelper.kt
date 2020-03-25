package com.crashops.sdk.data

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.app.ActivityCompat
import com.crashops.sdk.COHostApplication
import com.crashops.sdk.util.SdkLogger
import com.crashops.sdk.util.Strings
import java.io.File

internal class FilesHelper {
    private val context: Context
    private val contentPath: String
    private val legacySupport: String

    val cacheDir: File?
        get() = this.prepare(this.context.cacheDir)

    val externalCacheDir: File?
        get() {
            var file: File? = null
            if (this.isExternalStorageAvailable) {
//                if (Build.VERSION.SDK_INT >= 8) {
                    file = this.context.externalCacheDir
//                } else {
//                    file = File(Environment.getExternalStorageDirectory(), this.legacySupport + "/cache/" + this.contentPath)
//                }
            }

            return this.prepare(file)
        }

    val filesDir: File?
        get() = this.prepare(this.context.filesDir)

    val externalFilesDir: File?
        get() {
            var file: File? = null
            if (this.isExternalStorageAvailable) {
//                if (Build.VERSION.SDK_INT >= 8) {
                    file = this.context.getExternalFilesDir(null)
//                } else {
//                    file = File(Environment.getExternalStorageDirectory(), this.legacySupport + "/files/" + this.contentPath)
//                }
            }

            return this.prepare(file)
        }

    internal val isExternalStorageAvailable: Boolean
        get() {
            val state = Environment.getExternalStorageState()
            var isAvailable = false
            if ("mounted" != state) {
                SdkLogger.error(Strings.SDK_NAME, "External Storage is not mounted and/or writable\nHave you declared android.permission.WRITE_EXTERNAL_STORAGE in the manifest?")
                isAvailable = false
            } else {
                COHostApplication.sharedInstance().topActivity()?.let { presentedActivity ->
                    isAvailable = isPermittedForExternalStorage(presentedActivity)
                } ?: run {
                    isAvailable = true // Probably true, unable to verify permissions
                }
            }

            return isAvailable
        }

//    fun requestExternalStoragePermissions(activity: Activity) {
//        ActivityCompat.requestPermissions(activity,
//                arrayOf(
//                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
//                        Manifest.permission.READ_EXTERNAL_STORAGE
//                ), REQUEST_CODE
//        )
//    }

    fun isPermittedForExternalStorage(activity: Activity): Boolean {
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ActivityCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    init {
        checkNotNull(COHostApplication.sharedInstance()) { "Cannot get directory! Missing application context" }

        this.context = COHostApplication.sharedInstance()
        this.contentPath = Repository.FILES_PATH
        this.legacySupport = "Android/" + this.context.packageName
    }

    private fun prepare(file: File?): File? {
        if (file != null) {
            if (file.exists() || file.mkdirs()) {
                return file
            }

            SdkLogger.error(Strings.SDK_NAME, "Couldn't create file")
        } else {
            SdkLogger.log(Strings.SDK_NAME, "Null File")
        }

        return null
    }
}
