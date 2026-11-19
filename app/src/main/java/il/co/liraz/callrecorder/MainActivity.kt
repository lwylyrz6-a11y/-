package il.co.liraz.callrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var recordingFile: File? = null
    private var recordingStartedAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var timer: TextView
    private lateinit var recordButton: Button
    private lateinit var permissionsButton: Button
    private lateinit var recordingsButton: Button
    private lateinit var recordScreenButton: Button
    private var currentScreen = SCREEN_RECORD

    private val timerTask = object : Runnable {
        override fun run() {
            if (recorder != null) {
                val seconds = ((System.currentTimeMillis() - recordingStartedAt) / 1000).toInt()
                timer.text = formatDuration(seconds)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        showRecordScreen()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 24)
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
            setBackgroundColor(0xFFF5F6F8.toInt())
        }
        val title = TextView(this).apply {
            text = "מקליט שיחות"
            textSize = 28f
            setTextColor(0xFF111827.toInt())
            setPadding(0, 0, 0, 18)
        }
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }
        permissionsButton = navButton("אישורים") { showPermissionsScreen() }
        recordingsButton = navButton("הקלטות") { showRecordingsScreen() }
        recordScreenButton = navButton("מסך הקלטה") { showRecordScreen() }
        nav.addView(permissionsButton, weightedParams())
        nav.addView(recordingsButton, weightedParams())
        nav.addView(recordScreenButton, weightedParams())
        root.addView(title)
        root.addView(nav)
        setContentView(root)
    }

    private fun navButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            setOnClickListener { action() }
        }

    private fun weightedParams() = LinearLayout.LayoutParams(0, -2, 1f)

    private fun clearContent() {
        while (root.childCount > 2) root.removeViewAt(2)
        updateNavSelection()
    }

    private fun updateNavSelection() {
        permissionsButton.isSelected = currentScreen == SCREEN_PERMISSIONS
        recordingsButton.isSelected = currentScreen == SCREEN_RECORDINGS
        recordScreenButton.isSelected = currentScreen == SCREEN_RECORD
        permissionsButton.alpha = if (permissionsButton.isSelected) 1f else 0.72f
        recordingsButton.alpha = if (recordingsButton.isSelected) 1f else 0.72f
        recordScreenButton.alpha = if (recordScreenButton.isSelected) 1f else 0.72f
    }

    private fun showRecordScreen() {
        currentScreen = SCREEN_RECORD
        clearContent()
        status = TextView(this).apply {
            text = if (recorder == null) "מוכן להקלטה" else "ההקלטה פעילה"
            textSize = 18f
            setTextColor(0xFF1F2937.toInt())
            setPadding(0, 28, 0, 8)
        }
        timer = TextView(this).apply {
            text = if (recorder == null) "00:00:00" else formatDuration(((System.currentTimeMillis() - recordingStartedAt) / 1000).toInt())
            textSize = 34f
            setTextColor(0xFF111827.toInt())
            setPadding(0, 8, 0, 18)
        }
        val indicator = TextView(this).apply {
            text = if (recorder == null) "■  ההקלטה כבויה" else "■  מקליט עכשיו"
            textSize = 22f
            setTextColor(if (recorder == null) 0xFF6B7280.toInt() else 0xFFB91C1C.toInt())
            setPadding(0, 18, 0, 18)
        }
        recordButton = Button(this).apply {
            text = if (recorder == null) "●  התחלת הקלטה" else "■  עצירת הקלטה"
            textSize = 18f
            setOnClickListener {
                if (recorder == null) startRecording() else stopRecording()
                showRecordScreen()
            }
        }
        root.addView(status)
        root.addView(indicator)
        root.addView(timer)
        root.addView(recordButton)
        if (recorder != null) handler.post(timerTask)
        updateNavSelection()
    }

    private fun showPermissionsScreen() {
        currentScreen = SCREEN_PERMISSIONS
        clearContent()
        val heading = TextView(this).apply {
            text = "אישורים והרשאות"
            textSize = 22f
            setPadding(0, 24, 0, 16)
        }
        val mic = permissionStatus("מיקרופון", checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val phone = permissionStatus("זיהוי מצב שיחה", checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
        val overlayGranted = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
        val overlay = permissionStatus("הצגה מעל אפליקציות", overlayGranted)
        val grant = Button(this).apply {
            text = "אישור הרשאות הקלטה ושיחות"
            setOnClickListener {
                val requested = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 23) requested.add(Manifest.permission.READ_PHONE_STATE)
                requestPermissions(requested.toTypedArray(), REQUEST_PERMISSIONS)
            }
        }
        val overlayButton = Button(this).apply {
            text = "אישור הצגה מעל אפליקציות"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 23) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }
                }
            }
        }
        val refresh = Button(this).apply {
            text = "בדיקת הרשאות מחדש"
            setOnClickListener { showPermissionsScreen() }
        }
        root.addView(heading)
        root.addView(mic)
        root.addView(phone)
        root.addView(overlay)
        root.addView(grant)
        root.addView(overlayButton)
        root.addView(refresh)
        updateNavSelection()
    }

    private fun permissionStatus(label: String, granted: Boolean) = TextView(this).apply {
        text = (if (granted) "✓  " else "○  ") + label + if (granted) " – מאושר" else " – נדרש אישור"
        textSize = 17f
        setTextColor(if (granted) 0xFF166534.toInt() else 0xFFB45309.toInt())
        setPadding(0, 10, 0, 10)
    }

    private fun showRecordingsScreen() {
        currentScreen = SCREEN_RECORDINGS
        clearContent()
        root.addView(TextView(this).apply {
            text = "ההקלטות שלי"
            textSize = 22f
            setPadding(0, 24, 0, 14)
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        val dir = getExternalFilesDir("recordings") ?: filesDir
        val files = dir.listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) {
            list.addView(TextView(this).apply { text = "עדיין אין הקלטות שמורות"; textSize = 16f })
        } else files.forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            row.addView(TextView(this).apply {
                text = SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                textSize = 16f
            })
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
            }
            actions.addView(Button(this).apply { text = "ניגון"; setOnClickListener { playFile(file) } })
            actions.addView(Button(this).apply { text = "שיתוף"; setOnClickListener { shareFile(file) } })
            actions.addView(Button(this).apply { text = "מחיקה"; setOnClickListener { if (file.delete()) showRecordingsScreen() } })
            row.addView(actions)
            list.addView(row)
        }
        updateNavSelection()
    }

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_PERMISSIONS)
            Toast.makeText(this, "צריך לאשר שימוש במיקרופון", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val dir = getExternalFilesDir("recordings") ?: filesDir
            if (!dir.exists()) dir.mkdirs()
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            recordingFile = File(dir, "recording_$stamp.m4a")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(recordingFile!!.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recordingStartedAt = System.currentTimeMillis()
            Toast.makeText(this, "ההקלטה התחילה", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            recorder?.release()
            recorder = null
            recordingFile?.delete()
            recordingFile = null
            Toast.makeText(this, "לא ניתן להתחיל הקלטה. בדקי את הרשאות המיקרופון.", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        try {
            recorder?.stop()
            Toast.makeText(this, "ההקלטה נשמרה", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            recordingFile?.delete()
            Toast.makeText(this, "ההקלטה קצרה מדי ולא נשמרה", Toast.LENGTH_LONG).show()
        } finally {
            recorder?.release()
            recorder = null
            recordingFile = null
            handler.removeCallbacks(timerTask)
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    private fun playFile(file: File) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { it.release(); player = null }
                prepare()
                start()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "לא ניתן לנגן את ההקלטה", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "שיתוף הקלטה"))
        } catch (_: Exception) {
            Toast.makeText(this, "לא ניתן לשתף את ההקלטה", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized && currentScreen == SCREEN_PERMISSIONS) showPermissionsScreen()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerTask)
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 10
        private const val SCREEN_PERMISSIONS = 0
        private const val SCREEN_RECORDINGS = 1
        private const val SCREEN_RECORD = 2
    }
}
