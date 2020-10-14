package com.crashops.sdk.communication

import android.app.Activity
import android.os.Build
import com.crashops.sdk.data.Repository
import com.crashops.sdk.util.Constants
import com.crashops.sdk.util.SdkLogger
import com.crashops.sdk.util.Strings
import com.crashops.sdk.util.Utils
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import okhttp3.*

class Communicator {

    private var _appKey: String? = null
    private val appKey: String?
        get() {
            if (_appKey != null) return _appKey

            _appKey = Repository.instance.loadCustomValue(Constants.Keys.AppKey)
            return _appKey
        }

    private val callbacks: HashMap<Int, Utils.Callback<Pair<Int, String?>?>> = hashMapOf()

    companion object {
        @JvmStatic
        val instance: Communicator = Communicator()

        private val TAG: String = Communicator::class.java.simpleName

        /**
         * Server's endpoint that will receive all application startup pings.
         */
        const val PingUrl = "https://crashops.com/api/ping"

        /**
         * Server's endpoint that will receive all logs.
         */
        const val LogsServerUrl = "https://crashops.com/api/reports"

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()
        }

        fun removeCallbacks(key: Any) {
            removeCallbacks(key.hashCode())
        }

        fun removeCallbacks(key: Int) {
            instance.callbacks.remove(key)
        }
    }

    //TODO Try this later: https://github.com/gildor/kotlin-coroutines-okhttp
    private fun apiCall(request: Request, callerKey: Any, callback: Utils.Callback<Pair<Int, String?>?>) {
        val callerKeyHashCode = storeCallback(callerKey, callback) ?: return

        client.newCall(request)
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        SdkLogger.error(TAG, e)
//                    instance.callbacks[callerKeyHashCode]?.forEach {
//                        it.onCallback(null)
//                    }

                        if (e is SocketTimeoutException) {
                            Utils.debugToast("Check your internet connection...")
                        }

                        callback.onCallback(null)
                        removeCallbacks(callerKeyHashCode)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        SdkLogger.log(TAG, response)
//                    instance.callbacks[callerKeyHashCode]?.forEach {
//                        it.onCallback(response.body()?.string())
//                    }

                        val responseBody: String? = if (!response.isSuccessful) {
                            SdkLogger.error(TAG, response)
                            null
                        } else {
                            response.body()?.string()
                        }

                        if (responseBody == null) {
                            //response?.message()?.contains("not found")
                            response.body()?.string()?.let { responseBodyString ->
                                SdkLogger.error(TAG, responseBodyString)
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

                        callback.onCallback(Pair(response.code(), responseBody))
                        removeCallbacks(callerKeyHashCode)
                    }
                })
    }

    private fun apiCall(url: String, jsonString: String? = null, callerKey: Any, callback: Utils.Callback<Pair<Int, String?>?>) {
        val crashOpsAppKey = appKey ?: run {
            callback.onCallback(null)
            return
        }

        if (crashOpsAppKey.isEmpty()) {
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
                            .addHeader("crashops-application-key", crashOpsAppKey)
                            .post(RequestBody.create(
                                    MediaType.parse("application/json; charset=utf-8"),
                                    jsonString))
                            .url(url)
                            .build()
        } ?: run {
            _request =
                    Request.Builder()
                            .addHeader("crashops-application-key", crashOpsAppKey)
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
            callback: Utils.Callback<Pair<Int, String?>?>
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
    fun report(jsonString: String, callback: (Any?) -> Unit) {
        val serverUrl = LogsServerUrl
        apiCall(serverUrl, jsonString, this, object : Utils.Callback<Pair<Int, String?>?> {
            override fun onCallback(result: Pair<Int, String?>?) {
                callback.invoke(result)
            }
        })
    }

    @Throws(IOException::class)
    fun sendPresence(jsonString: String, callback: (Any?) -> Unit) {
        val serverUrl = PingUrl
        apiCall(serverUrl, jsonString, this, object : Utils.Callback<Pair<Int, String?>?> {
            override fun onCallback(result: Pair<Int, String?>?) {
                callback.invoke(result)
            }
        })
    }
}