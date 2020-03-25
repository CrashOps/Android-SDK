package com.crashops.sdk.communication

import android.app.Activity
import android.os.Build
import com.crashops.sdk.data.Repository
import com.crashops.sdk.util.Constants
import com.crashops.sdk.util.SdkLogger
import com.crashops.sdk.util.Strings
import com.crashops.sdk.util.Utils
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import okhttp3.*

class Communicator {

    private var _clientId: String? = null
    private val clientId: String?
        get() {
            if (_clientId != null) return _clientId

            _clientId = Repository.instance.loadCustomValue(Constants.Keys.ClientId)
            return _clientId
        }

    private val callbacks: HashMap<Int, Utils.Callback<Any?>> = hashMapOf()

    companion object {
        @JvmStatic
        val instance: Communicator = Communicator()

        private val TAG: String = Communicator::class.java.simpleName

        /**
         * Use this URL to test logs, it will save the logs in Firebase.
         */
        const val CrashesTestServerUrl = "https://us-central1-crash-logs.cloudfunctions.net/storeCrashReport"
        const val ErrorsTestServerUrl = "https://us-central1-crash-logs.cloudfunctions.net/storeErrorReport"

        /**
         * Uploads anything we want
         */
        const val UploadTestServerUrl = "https://us-central1-crash-logs.cloudfunctions.net/uploadLog"

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()
        }

        fun removeCallbacks(key: Any) {
            removeCallbacks(key.hashCode())
        }

        fun removeCallbacks(key: Int) {
            instance.callbacks.remove(key)
        }

        enum class Responses {
            NOT_FOUND
        }
    }

    // Try this later: https://github.com/gildor/kotlin-coroutines-okhttp
    private fun apiCall(request: Request, callerKey: Any, callback: Utils.Callback<Any?>) {
        val callerKeyHashCode = storeCallback(callerKey, callback) ?: return

        client.newCall(request)
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        SdkLogger.error(TAG, e)
//                    instance.callbacks[callerKeyHashCode]?.forEach {
//                        it.onCallback(null)
//                    }

                        if (e is SocketTimeoutException) {
                            Utils.toast("Check your internet connection...")
                        }

                        callback.onCallback(null)
                        removeCallbacks(callerKeyHashCode)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        SdkLogger.log(TAG, response)
//                    instance.callbacks[callerKeyHashCode]?.forEach {
//                        it.onCallback(response.body()?.string())
//                    }

                        if (response.code() == 429) {
                            SdkLogger.error(TAG, "We need to pay Firebase .... :P")
                        }

                        var responseBody = if (!response.isSuccessful) {
                            SdkLogger.error(TAG, response)
                            null
                        } else {
                            response.body()?.string()
                        }

                        if (responseBody == null) {
                            //response?.message()?.contains("not found")
                            response.body()?.string()?.let { responseBodyString ->
                                val message: String = try {
                                    var m = JSONObject(responseBodyString).optString("message", Strings.EMPTY)
                                    if (m.isEmpty()) {
                                        m = responseBodyString
                                    }

                                    m
                                } catch (e: Exception) {
                                    "<parsing failed>"
                                }

                                SdkLogger.error(TAG, message)
//                                Repository.instance.deviceId()?.let { id ->
//                                    if (message.contains(id) && message.contains("not found")) {
//                                        responseBody = Responses.NOT_FOUND.name
//                                    }
//                                }
                            }
                        }

                        callback.onCallback(responseBody)
                        removeCallbacks(callerKeyHashCode)
                    }
                })
    }

    private fun apiCall(url: String, jsonString: String? = null, callerKey: Any, callback: Utils.Callback<Any?>) {
        val crashOpsClientId = clientId ?: run {
            callback.onCallback(null)
            return
        }

        if (crashOpsClientId.isEmpty()) {
            callback.onCallback(null)
            return
        }

        if (jsonString != null) {
            // Validate
            try {
                // validated jsonObject
                JSONObject(jsonString)
            } catch (e: Exception) {
                callback.onCallback(null)
                return
            }
        }

        var _request: Request? = null
        jsonString?.let {
            _request =
                    Request.Builder()
                            .addHeader("crashops-client-id", crashOpsClientId)
                            .post(RequestBody.create(
                                    MediaType.parse("application/json; charset=utf-8"),
                                    jsonString))
                            .url(url)
                            .build()
        } ?: run {
            _request =
                    Request.Builder()
                            .addHeader("crashops-client-id", crashOpsClientId)
                            .get()
                            .url(url)
                            .build()
        }

        val request = _request ?: run {
            callback.onCallback(null)
            return
        }

        apiCall(request, callerKey, callback)
    }

    private fun storeCallback(
            callerKey: Any,
            callback: Utils.Callback<Any?>
    ): Int? {
        var isAlive = true
        if (callerKey is Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                isAlive = isAlive && !((callerKey as? Activity)?.isDestroyed ?: false)
            }

            isAlive = isAlive && !((callerKey as? Activity)?.isFinishing ?: false)
        }

        val callerKeyHashCode = callerKey.hashCode()
        return if (isAlive) {
//            val list = instance.callbacks[callerKeyHashCode] ?: arrayListOf()
//            list.add(callback)
//            instance.callbacks[callerKeyHashCode] = list
            instance.callbacks[callerKeyHashCode] = callback
            callerKeyHashCode
        } else {
            SdkLogger.error(TAG, "Missing callback for '$callerKey' - aborting call")
            null
        }
    }

    @Throws(IOException::class)
    fun reportViaRequest(url: String, file: File, callback: (Any?) -> Unit) {
        apiCall(url, file.readText(), this, object : Utils.Callback<Any?> {
            override fun onCallback(result: Any?) {
                callback.invoke(result)
            }
        })
    }


    @Throws(IOException::class)
    fun reportViaUpload(url: String, file: File, callback: (Any?) -> Unit) {
        val crashOpsClientId = clientId ?: run {
            callback.invoke(null)
            return
        }

        val mediaType = if (file.name.endsWith("zip")) {
            MediaType.parse("application/zip")
        } else {
            // https://www.sitepoint.com/mime-types-complete-list/
            MediaType.parse("text/plain")
        }

        val requestBody: RequestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("logFile", file.name, RequestBody.create(mediaType, file))
                .build()
        val request: Request = Request.Builder()
                .url(url)
                .header("crashops-client-id", crashOpsClientId)
                .header("Content-Type", "multipart/form-data")
                .post(requestBody).build()


        client.newCall(request)
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        SdkLogger.error(TAG, e)
//                    instance.callbacks[callerKeyHashCode]?.forEach {
//                        it.onCallback(null)
//                    }

                        if (e is SocketTimeoutException) {
                            Utils.toast("Check your internet connection...")
                        }

                        callback.invoke(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        SdkLogger.log(TAG, response)
//                    instance.callbacks[callerKeyHashCode]?.forEach {
//                        it.onCallback(response.body()?.string())
//                    }

                        if (response.code() == 429) {
                            SdkLogger.error(TAG, "We need to pay Firebase .... :P")
                        }

                        var responseBody = if (!response.isSuccessful) {
                            SdkLogger.error(TAG, response)
                            null
                        } else {
                            response.body()?.string()
                        }

                        if (responseBody == null) {
                            //response?.message()?.contains("not found")
                            response.body()?.string()?.let { responseBodyString ->
                                val message: String = try {
                                    var m = JSONObject(responseBodyString).optString("message", Strings.EMPTY)
                                    if (m.isEmpty()) {
                                        m = responseBodyString
                                    }

                                    m
                                } catch (e: Exception) {
                                    "<parsing failed>"
                                }

                                SdkLogger.error(TAG, message)
//                                Repository.instance.deviceId()?.let { id ->
//                                    if (message.contains(id) && message.contains("not found")) {
//                                        responseBody = Responses.NOT_FOUND.name
//                                    }
//                                }
                            }
                        }

                        callback.invoke(responseBody)
                    }
                })
    }
}