package com.crashops.sdk.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.TextUtils
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.crashops.sdk.BuildConfig
import com.crashops.sdk.COHostApplication

class PrivateEventBus {
    object Action {
        const val APPLICATION_GOING_BACKGROUND = Strings.SDK_IDENTIFIER + " - app goes background"
        const val APPLICATION_GOING_FOREGROUND = Strings.SDK_IDENTIFIER + " - app's back to foreground"
        const val ON_NETWORK_ACTIVE = Strings.SDK_IDENTIFIER + " - ON_NETWORK_ACTIVE"
    }

    interface BroadcastReceiverListener {
        fun onBroadcastReceived(intent: Intent, receiver: Receiver)
    }

    class Receiver

    /**
     * The receiver will live as long as the context lives. Therefore we will pass the application context in most of the times.
     * @param actions
     */
    constructor(context: Context?, private val actions: Collection<String>) : BroadcastReceiver() {
        private var receiverListener: BroadcastReceiverListener? = null

        init {
            if (actions.isNotEmpty()) {
                val intentFilter = IntentFilter()
                for (actionToListen in actions) {
                    intentFilter.addAction(actionToListen)
                }
                LocalBroadcastManager.getInstance(context ?: COHostApplication.shared()).registerReceiver(this, intentFilter)
            }
        }

        fun setListener(listener: BroadcastReceiverListener) {
            this.receiverListener = listener
        }

        override fun onReceive(context: Context, intent: Intent) {
            receivedAction(intent)
        }

        private fun receivedAction(intent: Intent?) {
            receiverListener?.let {
                if (intent != null && !TextUtils.isEmpty(intent.action)) {
                    it.onBroadcastReceived(intent, this)
                }
            } ?: run {
                SdkLogger.error(TAG, "onBroadcastReceived: Missing listener! Intent == " + intent!!)
            }
        }

        override fun toString(): String {
            return actions.toString()
        }

        fun quit() {
            try {
                LocalBroadcastManager.getInstance(COHostApplication.shared()).unregisterReceiver(this)
                SdkLogger.log(TAG, "removeReceiver: quit successfully: $this")
            } catch (e: Exception) {
                SdkLogger.error(TAG, "removeReceiver: couldn't quitReceiving receiver: " + e.message)
            }

            receiverListener = null
        }
    }

    companion object {
        private val TAG: String = PrivateEventBus::class.java.simpleName

        fun createNewReceiver(context: Context? = null, listener: BroadcastReceiverListener, vararg actions: String): Receiver {
            val receiver = Receiver(context, actions.asList())
            receiver.setListener(listener)
            return receiver
        }

        fun createDisposalReceiver(context: Context? = null, listener: BroadcastReceiverListener, vararg actions: String): Receiver {
            val disposalReceiver = Receiver(context, actions.asList())
            disposalReceiver.setListener(object : BroadcastReceiverListener {
                override fun onBroadcastReceived(intent: Intent, receiver: Receiver) {
                    listener.onBroadcastReceived(intent, receiver)
                    SdkLogger.log(disposalReceiver)
                    receiver.quit()
                }

            })

            return disposalReceiver
        }

        @JvmOverloads
        @JvmStatic
        fun notify(action: String, extraValues: Bundle? = null, context: Context? = null) {
            val applicationContext = context ?: COHostApplication.sharedInstance()
            val broadcastIntent = Intent(applicationContext, PrivateEventBus::class.java).setAction(action)
            extraValues?.let {
                if (it.size() > 0) {
                    broadcastIntent.putExtras(it)
                }
            }

//            broadcastIntent.setAction()
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(broadcastIntent)
        }

        fun cleanup() {
            //LocalBroadcastManager.getInstance(COHostApplication.instance())
        }
    }
}