package il.co.liraz.callrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCallRecording()
            ACTION_STOP -> stopCallRecording()
        }
        return START_NOT_STICKY
    }

    private fun startCallRecording() {
        if (recorder != null) return
        createChannel()
        startedAt = System.currentTimeMillis()
        val notification = buildNotification("מקליט שיחה", true)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        try {
            val dir = getExternalFilesDir("recordings") ?: filesDir
            if (!dir.exists()) dir.mkdirs()
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            output = File(dir, "call_$stamp.m4a")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(output!!.absolutePath)
            r.prepare()
            r.start()
            recorder = r
        } catch (_: Exception) {
            recorder?.release()
            recorder = null
            output?.delete()
            output = null
            updateNotification("זוהתה שיחה – ההקלטה נחסמה על ידי המכשיר", false)
        }
    }

    private fun stopCallRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
            output?.delete()
        } finally {
            recorder?.release()
            recorder = null
            output = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "חיווי הקלטת שיחה", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String, chronometer: Boolean): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("■  מקליט שיחות")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFF000000.toInt())
        if (chronometer) {
            builder.setWhen(startedAt).setUsesChronometer(true)
        }
        return builder.build()
    }

    private fun updateNotification(text: String, chronometer: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text, chronometer))
    }

    override fun onDestroy() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "il.co.liraz.callrecorder.START_CALL_RECORDING"
        const val ACTION_STOP = "il.co.liraz.callrecorder.STOP_CALL_RECORDING"
        private const val CHANNEL_ID = "call_recording"
        private const val NOTIFICATION_ID = 71
    }
}
