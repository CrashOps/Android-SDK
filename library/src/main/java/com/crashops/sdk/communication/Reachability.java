package com.crashops.sdk.communication;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import androidx.annotation.Nullable;

import com.crashops.sdk.CrashOps;
import com.crashops.sdk.util.PrivateEventBus;

public enum Reachability {
    instance;

    Reachability() {
        wifiManager = (WifiManager) CrashOps.getApplicationContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) CrashOps.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo wifiNetworkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        }

    }

    public enum NetworkStatus {
        Unknown, Cellular, WiFi, None;

        @Override
        public String toString() {
            return name();
        }
    }

    private NetworkStatus status;

    @Nullable
    private final ConnectivityManager connectivityManager;
    @Nullable
    private final WifiManager wifiManager;

    public NetworkStatus getReachabilityStatus() {
        status = NetworkStatus.Unknown;
        Boolean isConnectedToWifi = isConnectedToWifi();
        Boolean isConnectedToCellularData = isConnectedToCellularData();
        if (isConnectedToWifi != null && isConnectedToWifi) {
            status = NetworkStatus.WiFi;
        } else if (isConnectedToCellularData != null && isConnectedToCellularData) {
            status = NetworkStatus.Cellular;
        } else if (wifiManager != null && connectivityManager != null) {
            status = NetworkStatus.None;
        }

        return status;
    }

    public NetworkStatus getStatus() {
        return status;
    }

    @Nullable
    Boolean isConnectedToCellularData() {
        if (connectivityManager == null) return null;
        NetworkInfo ni = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectivityManager.addDefaultNetworkActiveListener(new ConnectivityManager.OnNetworkActiveListener() {
                @Override
                public void onNetworkActive() {
                    PrivateEventBus.notify(PrivateEventBus.Action.ON_NETWORK_ACTIVE);
                }
            });
        }

        // The 'ni == null' means that this device does not have mobile data capability
        return ni != null && ni.isAvailable();
    }

    @Nullable
    private Boolean isConnectedToWifi() {
        if (connectivityManager == null) return null;
        NetworkInfo ni = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        // The 'ni == null' means that this device does not have mobile data capability
        return ni != null && ni.isAvailable();
    }

    @Nullable
    private Boolean isWifiEnabled() {
        if (wifiManager == null) return null;
        return wifiManager.isWifiEnabled();
    }
}
