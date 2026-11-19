package il.co.liraz.callrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L
    private var overlay: TextView? = null
    private var windowManager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())

    private val overlayTicker = object : Runnable {
        override fun run() {
            val view = overlay ?: return
            val elapsed = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
            val h = elapsed / 3600
            val m = (elapsed % 3600) / 60
            val s = elapsed % 60
            view.text = "■ REC  " + String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
            handler.postDelayed(this, 1000)
        }
    }

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
        val notification = buildNotification("מתבצעת הקלטה", true)
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
            showOverlay()
        } catch (_: Exception) {
            recorder?.release()
            recorder = null
            output?.delete()
            output = null
            updateNotification("זוהתה שיחה – ההקלטה לא התחילה", false)
            removeOverlay()
        }
    }

    private fun showOverlay() {
        if (Build.VERSION.SDK_INT < 23 || !Settings.canDrawOverlays(this)) return
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = TextView(this).apply {
                text = "■ REC  00:00:00"
                textSize = 13f
                setTextColor(Color.WHITE)
                setTypeface(Typeface.DEFAULT_BOLD)
                setBackgroundColor(Color.BLACK)
                setPadding(16, 12, 16, 12)
                elevation = 12f
            }
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                       else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 18
                y = 140
            }
            windowManager?.addView(view, params)
            overlay = view
            handler.post(overlayTicker)
        } catch (_: Exception) {
            removeOverlay()
        }
    }

    private fun removeOverlay() {
        handler.removeCallbacks(overlayTicker)
        try {
            overlay?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        overlay = null
        windowManager = null
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
            removeOverlay()
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
            .setColor(Color.BLACK)
        if (chronometer) builder.setWhen(startedAt).setUsesChronometer(true)
        return builder.build()
    }

    private fun updateNotification(text: String, chronometer: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text, chronometer))
    }

    override fun onDestroy() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        removeOverlay()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "il.co.liraz.callrecorder.START_CALL_RECORDING"
        const val ACTION_STOP = "il.co.liraz.callrecorder.STOP_CALL_RECORDING"
        private const val CHANNEL_ID = "call_recording"
        private const val NOTIFICATION_ID = 71
    }
}
