package com.crashops.sdk.util

class Constants {
    companion object {
        const val ONE_SECOND_MILLISECONDS: Long = 1000
        const val ONE_MINUTE_MILLISECONDS: Long = ONE_SECOND_MILLISECONDS * 60
        const val ONE_HOUR_MILLISECONDS: Long = ONE_MINUTE_MILLISECONDS * 60
    }

    object Keys {
        const val API_KEY: String = "${Strings.SDK_NAME_LOWERCASED}_api_key"
        const val UploadIds: String = "${Strings.SDK_NAME}_UploadIds"
        const val LogsPersistenceFileName = "${Strings.SDK_NAME}_LogsService"
        const val GlobalPersistenceFileName = "${Strings.SDK_IDENTIFIER}_preferences"
        const val ClientId = "clientId"
        const val LastServiceCall = "${Strings.SDK_NAME}_lastServiceCall"
        const val DeviceId = "${Strings.SDK_NAME}_deviceId"
        const val DeviceDetails = "${Strings.SDK_NAME}_deviceDetails"

        const val HOST_APP_VERSION_NAME = "appVersion"
        const val HOST_APP_VERSION_CODE = "appVersionCode"
        const val HOST_APP_PACKAGE_NAME = "packageName"

        object Json {
            const val LOCAL_TIME = "localTime"
            const val ID = "id"
            const val IS_FATAL = "isFatal"
            const val ERROR_DETAILS = "errorDetails"
            const val ERROR_TITLE = "errorTitle"
            const val CRASH_MESSAGE = "message"
            const val CAUSE = "cause"
            const val TIMESTAMP = "timestamp"
            const val REPORT = "report"
            const val DEVICE_INFO = "deviceInfo"
            const val METADATA = "metadata"
            const val STACK_TRACE = "stackTrace"
            const val SESSION_ID = "sessionId"
            const val HOST_APP_DETAILS = "appDetails"
            const val REPORTED_THREAD = "reportedThread"
            const val OTHER_PROCESSES = "otherProcesses"
        }
    }

}