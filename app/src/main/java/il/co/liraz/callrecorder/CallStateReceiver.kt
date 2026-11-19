package il.co.liraz.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val action = when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> CallRecordingService.ACTION_START
            TelephonyManager.EXTRA_STATE_IDLE -> CallRecordingService.ACTION_STOP
            else -> return // ringing: do not record before the call is answered
        }
        val serviceIntent = Intent(context, CallRecordingService::class.java).setAction(action)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: Exception) {
            // Android/OEM background restrictions may prevent automatic startup.
        }
    }
}
