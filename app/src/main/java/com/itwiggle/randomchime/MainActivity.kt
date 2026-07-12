package com.itwiggle.randomchime

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var root: LinearLayout
    private var config = Settings()
    private var clips = mutableListOf<Clip>()
    private val green = Color.rgb(31, 107, 79)
    private val paper = Color.rgb(251, 252, 248)
    private val pickAudio = 41

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        prefs = AppPrefs(this)
        config = prefs.settings()
        clips = prefs.clips()
        requestNeededPermissions()
        draw()
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized) {
            config = prefs.settings()
            draw()
        }
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            startActivity(Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 14f, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.rgb(23, 32, 29))
        if (bold) setTypeface(typeface, 1)
        setPadding(0, dp(5), 0, dp(5))
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        setBackgroundColor(paper)
    }
    private fun button(label: String, primary: Boolean = true, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(if (primary) Color.WHITE else green)
        setBackgroundColor(if (primary) green else Color.WHITE)
        setOnClickListener { action() }
    }
    private fun row(vararg children: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        children.forEach { child -> addView(child, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }) }
    }
    private fun spacer() = Space(this).apply { minimumHeight = dp(14) }
    private fun section(value: String) = text(value, 23f, true)
    private fun saveAndDraw() { prefs.save(config); draw() }

    private fun timeButton(label: String, value: String, save: (String) -> Unit) = button("$label  $value", false) {
        val parts = value.split(":")
        TimePickerDialog(this, { _, hour, minute ->
            save("%02d:%02d".format(hour, minute))
            saveAndDraw()
        }, parts[0].toInt(), parts[1].toInt(), false).show()
    }

    private fun numberField(label: String, value: Int, save: (Int) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(label, 12f, true))
        addView(EditText(this@MainActivity).apply {
            inputType = 2
            setText(value.toString())
            setOnFocusChangeListener { _, focused -> if (!focused) save(text.toString().toIntOrNull() ?: value) }
        })
    }

    private fun draw() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(50))
            setBackgroundColor(Color.rgb(238, 241, 233))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        root.addView(text("RANDOM CHIME", 11f, true))
        root.addView(text("Let the day surprise you.", 30f, true))
        root.addView(spacer())
        drawStatus()
        drawSchedule()
        drawDeck()
        drawVerdict()
        drawHistory()
    }

    private fun drawStatus() {
        val status = card().apply { gravity = Gravity.CENTER }
        status.addView(text(if (config.armed) "ARMED" else "QUIET", 12f, true))
        val next = prefs.nextAt()
        val nextText = if (next > System.currentTimeMillis()) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(next)) else "Waiting to be armed"
        status.addView(text(nextText, 25f, true))
        status.addView(text(if (config.armed) "Android owns the alarm now—even while closed." else "Your settings and audio deck are stored on this phone", 12f))
        root.addView(status)
        root.addView(row(
            button(if (config.armed) "Disarm" else "Arm random prompts") {
                config.armed = !config.armed
                prefs.save(config)
                if (config.armed) AlarmScheduler.scheduleNext(this) else AlarmScheduler.cancel(this)
                draw()
            },
            button("Test in 10 sec", false) {
                config.armed = true
                prefs.save(config)
                AlarmScheduler.scheduleNext(this, System.currentTimeMillis() + 10_000)
                draw()
            }
        ))
        root.addView(spacer())
    }

    private fun drawSchedule() {
        val panel = card()
        panel.addView(section("Your surprise window"))
        panel.addView(row(timeButton("Start", config.start) { config.start = it }, timeButton("End", config.end) { config.end = it }))
        panel.addView(row(
            button(if (config.exactMode) "✓ Exact number" else "Exact number", !config.exactMode) { config.exactMode = true; saveAndDraw() },
            button(if (!config.exactMode) "✓ Random range" else "Random range", config.exactMode) { config.exactMode = false; saveAndDraw() }
        ))
        if (config.exactMode) panel.addView(numberField("Prompts per day", config.exactCount) { config.exactCount = it.coerceIn(1, 12); prefs.save(config) })
        else panel.addView(row(
            numberField("At least", config.minCount) { config.minCount = it.coerceIn(1, config.maxCount); prefs.save(config) },
            numberField("At most", config.maxCount) { config.maxCount = it.coerceIn(config.minCount, 12); prefs.save(config) }
        ))
        panel.addView(text("Minimum spacing: ${config.minGap} minutes", 12f, true))
        panel.addView(SeekBar(this).apply {
            max = 47; progress = (config.minGap / 5 - 1).coerceIn(0, 47)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { config.minGap = (progress + 1) * 5 }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) { prefs.save(config); draw() }
            })
        })
        root.addView(panel); root.addView(spacer())
    }

    private fun drawDeck() {
        val panel = card()
        panel.addView(section("Your voices of consequence"))
        panel.addView(button("＋ Add recordings") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, pickAudio)
        })
        panel.addView(text("${clips.count { it.enabled }} active · shuffle without repeats", 12f))
        if (clips.isEmpty()) panel.addView(text("No recordings yet. Android's alarm tone is the fallback."))
        clips.toList().forEachIndexed { index, clip -> panel.addView(clipRow(index, clip)) }
        root.addView(panel); root.addView(spacer())
    }

    private fun clipRow(index: Int, clip: Clip): View {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(EditText(this@MainActivity).apply { setText(clip.name); setSingleLine(); setOnFocusChangeListener { _, f -> if (!f) { clip.name = text.toString(); prefs.saveClips(clips) } } })
            addView(EditText(this@MainActivity).apply { setText(clip.category); setSingleLine(); textSize = 11f; setOnFocusChangeListener { _, f -> if (!f) { clip.category = text.toString(); prefs.saveClips(clips) } } })
        }
        return row(
            button("▶", false) { MediaPlayer.create(this, Uri.parse(clip.uri))?.start() }, fields,
            button(if (clip.enabled) "ON" else "OFF", false) { clip.enabled = !clip.enabled; prefs.saveClips(clips); draw() },
            button("×", false) { clips.removeAt(index); prefs.saveClips(clips); draw() }
        )
    }

    private fun drawVerdict() {
        val label = prefs.activeLabel() ?: return
        val panel = card().apply { gravity = Gravity.CENTER }
        panel.addView(text("THE VOICE HAS SPOKEN", 11f, true)); panel.addView(text("So—did you do it?", 27f, true)); panel.addView(text(label))
        panel.addView(button("✓ DONE") { sendService(PromptPlaybackService.DONE); draw() })
        panel.addView(button("Snooze 10 min — excuses > motivation", false) { sendService(PromptPlaybackService.SNOOZE); draw() })
        root.addView(panel); root.addView(spacer())
    }

    private fun drawHistory() {
        val panel = card(); panel.addView(section("Recent prompts")); val history = prefs.history()
        if (history.length() == 0) panel.addView(text("No prompts yet."))
        for (i in 0 until minOf(history.length(), 8)) {
            val item = history.getJSONObject(i); val outcome = item.optString("outcome")
            val icon = if (outcome == "done") "✓" else if (outcome == "snoozed") "↻" else "♪"
            panel.addView(text("$icon  ${item.getString("label")} · $outcome"))
        }
        panel.addView(button("Clear history", false) { prefs.clearHistory(); draw() }); root.addView(panel)
    }

    private fun sendService(action: String) { startService(Intent(this, PromptPlaybackService::class.java).setAction(action)) }

    @Deprecated("Legacy picker retained to avoid an additional UI dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickAudio || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>(); data.data?.let(uris::add); data.clipData?.let { list -> for (i in 0 until list.itemCount) uris.add(list.getItemAt(i).uri) }
        uris.distinct().forEach { uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            if (clips.none { it.uri == uri.toString() }) clips.add(Clip(uri.toString(), displayName(uri)))
        }
        prefs.saveClips(clips); draw()
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) return it.getString(0).substringBeforeLast('.') }
        return "Prompt"
    }
}
