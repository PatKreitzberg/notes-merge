package com.wyldsoft.notes.sdkintegration.onyx

import android.content.Context
import com.wyldsoft.notes.sdkintegration.GlobalDeviceReceiver
import com.wyldsoft.notes.sdkintegration.BaseDeviceReceiver

class OnyxDeviceReceiverWrapper(private val onyxReceiver: GlobalDeviceReceiver) : BaseDeviceReceiver() {

    override fun enable(context: Context, enable: Boolean) {
        onyxReceiver.enable(context, enable)
    }

    override fun setSystemNotificationPanelChangeListener(listener: (Boolean) -> Unit): BaseDeviceReceiver {
        onyxReceiver.setSystemNotificationPanelChangeListener { open ->
            listener(open)
        }
        return this
    }

    override fun setSystemScreenOnListener(listener: () -> Unit): BaseDeviceReceiver {
        onyxReceiver.setSystemScreenOnListener {
            listener()
        }
        return this
    }

    override fun cleanup() {
        // Any cleanup needed for Onyx device receiver
    }
}