package com.crashops.sdk.data;

import com.crashops.sdk.communication.Reachability;

public class DeviceDetails {
    private float batteryLevel;
    private Integer screenHeight;
    private Integer screenWidth;
    private Reachability.NetworkStatus reachabilityStatus;
    private String carrierName;
    private String systemVersion;
    private boolean isJailbroken = false; // Perry: "Aligning with the iOS SDK, instead of 'isRooted'"

    public Integer getScreenHeight() {
        return screenHeight;
    }

    public Integer getScreenWidth() {
        return screenWidth;
    }

    public Reachability.NetworkStatus getReachabilityStatus() {
        return reachabilityStatus;
    }

    public DeviceDetails(int screenWidth, int screenHeight, Reachability.NetworkStatus reachabilityStatus, boolean isRooted, float batteryLevel, String systemVersion, String carrierName) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.reachabilityStatus = reachabilityStatus;
        this.batteryLevel = batteryLevel;
        this.systemVersion = systemVersion;
        this.carrierName = carrierName;

        this.isJailbroken = isRooted; // Perry: "Yeah, I used different names on purpose, so developers could search it using 'isRooted' keyword"
    }

    public boolean isJailbroken() {
        return isJailbroken;
    }

    public String getSystemVersion() {
        return systemVersion;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public float getBatteryLevel() {
        return batteryLevel;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
