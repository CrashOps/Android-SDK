package com.crashops.sdk.communication

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

class Reachability {
    enum class NetworkStatus {
        Unknown, Cable, Cellular, WiFi, None;

        override fun toString(): String {
            return name
        }
    }

    companion object {
        @JvmStatic
        public fun getReachabilityStatus(context: Context): NetworkStatus {
            var result = NetworkStatus.Unknown
            val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val networkCapabilities = connectivityManager.activeNetwork ?: return result
                val actNw =
                        connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return result
                result = when {
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.WiFi
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.Cellular
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkStatus.Cable
                    else -> NetworkStatus.None
                }
            } else {
                connectivityManager.run {
                    connectivityManager.activeNetworkInfo?.run {
                        result = when (type) {
                            ConnectivityManager.TYPE_WIFI -> NetworkStatus.WiFi
                            ConnectivityManager.TYPE_MOBILE -> NetworkStatus.Cellular
                            ConnectivityManager.TYPE_ETHERNET -> NetworkStatus.Cable
                            else -> NetworkStatus.None
                        }

                    }
                }
            }

            return result
        }
    }
}