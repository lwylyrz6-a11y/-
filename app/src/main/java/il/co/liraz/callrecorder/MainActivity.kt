package il.co.liraz.callrecorder

import android.Manifest
import android.app.Activity
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var recordingFile: File? = null
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var recordButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }
        refreshRecordings()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }
        val title = TextView(this).apply { text = "מקליט שיחות"; textSize = 28f; setPadding(0, 0, 0, 16) }
        status = TextView(this).apply { text = "מוכן להקלטה"; textSize = 16f; setPadding(0, 0, 0, 18) }
        recordButton = Button(this).apply {
            text = "התחלת הקלטה"
            setOnClickListener { if (recorder == null) startRecording() else stopRecording() }
        }
        val note = TextView(this).apply {
            text = "חשוב: ההקלטה משתמשת במיקרופון. Android עשוי לחסום הקלטת שיחות או לאפשר לשמוע רק צד אחד. אין הבטחה להקלטת שני הצדדים."
            textSize = 14f
            setPadding(0, 18, 0, 18)
        }
        val heading = TextView(this).apply { text = "ההקלטות שלי"; textSize = 20f; setPadding(0, 12, 0, 8) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(title); root.addView(status); root.addView(recordButton); root.addView(note); root.addView(heading); root.addView(list)
        setContentView(root)
    }

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10); return
        }
        try {
            val dir = getExternalFilesDir("recordings") ?: filesDir
            if (!dir.exists()) dir.mkdirs()
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            recordingFile = File(dir, "recording_$stamp.m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(recordingFile!!.absolutePath)
            r.prepare(); r.start()
            recorder = r
            recordButton.text = "עצירת הקלטה"
            status.text = "מקליט כעת…"
        } catch (e: Exception) {
            recorder?.release(); recorder = null
            recordingFile?.delete(); recordingFile = null
            status.text = "לא ניתן להתחיל הקלטה"
            Toast.makeText(this, "בדקי הרשאת מיקרופון ונסי שוב", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        try { recorder?.stop() } catch (_: Exception) { recordingFile?.delete() }
        recorder?.release(); recorder = null
        recordButton.text = "התחלת הקלטה"
        status.text = "ההקלטה נשמרה"
        refreshRecordings()
    }

    private fun refreshRecordings() {
        list.removeAllViews()
        val dir = getExternalFilesDir("recordings") ?: filesDir
        val files = dir.listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) {
            list.addView(TextView(this).apply { text = "עדיין אין הקלטות"; textSize = 15f })
            return
        }
        files.forEach { file ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL }
            val name = TextView(this).apply {
                text = file.name; textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val play = Button(this).apply { text = "ניגון"; setOnClickListener { playFile(file) } }
            row.addView(name); row.addView(play); list.addView(row)
        }
    }

    private fun playFile(file: File) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { it.release(); player = null }
                prepare(); start()
            }
            Toast.makeText(this, "מנגן הקלטה", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "לא ניתן לנגן את הקובץ", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        player?.release(); player = null
        super.onDestroy()
    }
}
