package com.crashops.sdk.util

import java.text.SimpleDateFormat
import java.util.*

class Strings {
    companion object {
        const val SDK_NAME = "CrashOps"
        const val SDK_IDENTIFIER = "com.crashops.sdk"
        const val SDK_NAME_LOWERCASED = "crashops"
        const val TestedExceptionName = "$SDK_NAME Tested Exception"

        @Suppress("MayBeConstant") // Don't wanna, it will cause redundant allocations :P
        val EMPTY = ""

        fun lengthString(lengthMilliseconds: Long): String {
            val seconds = lengthMilliseconds / Constants.ONE_SECOND_MILLISECONDS
            val minutes = lengthMilliseconds / Constants.ONE_MINUTE_MILLISECONDS
            val hours = lengthMilliseconds / Constants.ONE_HOUR_MILLISECONDS
            val days = hours / 24

            if (days > 0) {
                return "$days ${plural(days.toInt(), "day")}"
            }

            if (hours > 0L) {
                val minutesInHour = minutes % 60
                val minutesString: String = if (minutesInHour > 10L) {
                    " and $minutesInHour ${plural(minutesInHour.toInt(), "minute")}"
                } else {
                    ""
                }

                return "$hours ${plural(hours.toInt(), "hour")}$minutesString"
            }

            if (minutes > 0L) {
                return "$minutes ${plural(minutes.toInt(), "minute")}"
            }

            return "$seconds ${plural(seconds.toInt(), "second")}"
        }

        private fun plural(num: Int, phrase: String): String {
            return if (num > 1 || num == 0) {
                "${phrase}s"
            } else {
                phrase
            }
        }

        fun date(date: Date, dateFormat: String): String {
            return SimpleDateFormat(dateFormat, Locale.UK).format(date)
        }

        fun timestamp(timestamp: Long, dateFormat: String): String {
            return date(Date(timestamp), dateFormat)
        }

        fun now(dateFormat: String): String {
            return timestamp(Utils.now(), dateFormat)
        }
    }
}
