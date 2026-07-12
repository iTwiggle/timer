package com.itwiggle.randomchime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Clip(val uri: String, var name: String, var category: String = "Motivation", var enabled: Boolean = true)
data class TextPrompt(val id: String, var text: String, var category: String = "Mindset", var intensity: String = "firm", var enabled: Boolean = true)
data class PromptPayload(val audioUri: String?, val audioLabel: String, val audioCategory: String, val textId: String, val message: String, val messageCategory: String)
data class Settings(
    var start: String = "09:00", var end: String = "21:00", var exactMode: Boolean = false,
    var exactCount: Int = 2, var minCount: Int = 1, var maxCount: Int = 3, var minGap: Int = 60,
    var volume: Int = 70, var vibrate: Boolean = true, var shuffle: Boolean = true,
    var days: Set<Int> = (1..7).toSet(), var armed: Boolean = false
)

class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("random_chime", Context.MODE_PRIVATE)

    fun settings(): Settings {
        val j = JSONObject(p.getString("settings", "{}")!!)
        return Settings(j.optString("start", "09:00"), j.optString("end", "21:00"), j.optBoolean("exactMode", false),
            j.optInt("exactCount", 2), j.optInt("minCount", 1), j.optInt("maxCount", 3), j.optInt("minGap", 60),
            j.optInt("volume", 70), j.optBoolean("vibrate", true), j.optBoolean("shuffle", true),
            j.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: (1..7).toSet(), j.optBoolean("armed", false))
    }
    fun save(s: Settings) {
        val j = JSONObject().put("start", s.start).put("end", s.end).put("exactMode", s.exactMode).put("exactCount", s.exactCount)
            .put("minCount", s.minCount).put("maxCount", s.maxCount).put("minGap", s.minGap).put("volume", s.volume)
            .put("vibrate", s.vibrate).put("shuffle", s.shuffle).put("days", JSONArray(s.days.toList())).put("armed", s.armed)
        p.edit().putString("settings", j.toString()).apply()
    }

    fun clips(): MutableList<Clip> {
        val a = JSONArray(p.getString("clips", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }.map { Clip(it.getString("uri"), it.optString("name", "Prompt"), it.optString("category", "Motivation"), it.optBoolean("enabled", true)) }.toMutableList()
    }
    fun saveClips(items: List<Clip>) {
        val a = JSONArray(); items.forEach { a.put(JSONObject().put("uri", it.uri).put("name", it.name).put("category", it.category).put("enabled", it.enabled)) }
        p.edit().putString("clips", a.toString()).apply()
    }
    fun nextClip(): Clip? {
        val enabled = clips().filter { it.enabled }; if (enabled.isEmpty()) return null; if (!settings().shuffle) return enabled.random()
        var ids = jsonStrings("audio_deck").filter { id -> enabled.any { it.uri == id } }.toMutableList()
        if (ids.isEmpty()) ids = enabled.map { it.uri }.shuffled().toMutableList()
        val id = ids.removeAt(0); saveStrings("audio_deck", ids); return enabled.firstOrNull { it.uri == id }
    }

    private val defaults = listOf(
        TextPrompt("default-1", "Nobody is coming. Start anyway.", "Mindset", "firm"),
        TextPrompt("default-2", "You asked for the interruption. Earn it.", "Accountability", "firm"),
        TextPrompt("default-3", "The couch has no long-term plan for you.", "Movement", "unhinged"),
        TextPrompt("default-4", "Motivation missed the meeting. Discipline showed up.", "Discipline", "firm"),
        TextPrompt("default-5", "Do the next honest thing.", "Mindset", "supportive"),
        TextPrompt("default-6", "Future you has filed a complaint.", "Accountability", "unhinged"),
        TextPrompt("default-7", "A ten-minute effort still counts.", "Momentum", "supportive"),
        TextPrompt("default-8", "Your excuse has been noted and denied.", "Discipline", "unhinged"),
        TextPrompt("default-9", "Movement first. Negotiations later.", "Movement", "firm"),
        TextPrompt("default-10", "The task will not become less real while ignored.", "Accountability", "firm"),
        TextPrompt("default-11", "You do not need to feel ready.", "Momentum", "supportive"),
        TextPrompt("default-12", "Merry Christmas, motherfucker. Get moving.", "Chaos", "unhinged")
    )
    fun textPrompts(): MutableList<TextPrompt> {
        val raw = p.getString("text_prompts", null) ?: return defaults.map { it.copy() }.toMutableList().also { saveTextPrompts(it) }
        val a = JSONArray(raw); return (0 until a.length()).map { a.getJSONObject(it) }.map { TextPrompt(it.getString("id"), it.getString("text"), it.optString("category", "Mindset"), it.optString("intensity", "firm"), it.optBoolean("enabled", true)) }.toMutableList()
    }
    fun saveTextPrompts(items: List<TextPrompt>) {
        val a = JSONArray(); items.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("category", it.category).put("intensity", it.intensity).put("enabled", it.enabled)) }
        p.edit().putString("text_prompts", a.toString()).apply()
    }
    fun addTextPrompts(lines: List<String>) {
        val items = textPrompts(); lines.map { it.trim() }.filter { it.isNotBlank() }.forEach { text -> if (items.none { it.text.equals(text, true) }) items.add(TextPrompt(UUID.randomUUID().toString(), text)) }
        saveTextPrompts(items)
    }
    fun nextTextPrompt(): TextPrompt {
        val enabled = textPrompts().filter { it.enabled }.ifEmpty { defaults }
        var ids = jsonStrings("text_deck").filter { id -> enabled.any { it.id == id } }.toMutableList()
        if (ids.isEmpty()) ids = enabled.map { it.id }.shuffled().toMutableList()
        val id = ids.removeAt(0); saveStrings("text_deck", ids); return enabled.first { it.id == id }
    }

    fun setActivePayload(value: PromptPayload?) { p.edit().apply { if (value == null) remove("active_payload") else putString("active_payload", payloadJson(value).toString()) }.apply() }
    fun activePayload(): PromptPayload? = p.getString("active_payload", null)?.let { parsePayload(JSONObject(it)) }
    fun prepareRetry() { activePayload()?.let { p.edit().putString("retry_payload", payloadJson(it).toString()).apply() } }
    fun retryPayload(): PromptPayload? = p.getString("retry_payload", null)?.let { parsePayload(JSONObject(it)) }
    fun clearRetryPayload() { p.edit().remove("retry_payload").apply() }
    private fun payloadJson(v: PromptPayload) = JSONObject().put("audioUri", v.audioUri).put("audioLabel", v.audioLabel).put("audioCategory", v.audioCategory).put("textId", v.textId).put("message", v.message).put("messageCategory", v.messageCategory)
    private fun parsePayload(j: JSONObject) = PromptPayload(j.optString("audioUri").takeIf { it.isNotBlank() && it != "null" }, j.optString("audioLabel", "Random Chime"), j.optString("audioCategory", "Prompt"), j.optString("textId"), j.optString("message"), j.optString("messageCategory", "Mindset"))

    fun startEvent(payload: PromptPayload, scheduledAt: Long, isRetry: Boolean): String {
        val id = UUID.randomUUID().toString(); val now = System.currentTimeMillis()
        val event = JSONObject().put("id", id).put("scheduledAt", scheduledAt).put("firedAt", now).put("respondedAt", JSONObject.NULL)
            .put("audioLabel", payload.audioLabel).put("audioCategory", payload.audioCategory).put("textId", payload.textId)
            .put("message", payload.message).put("messageCategory", payload.messageCategory).put("isRetry", isRetry)
            .put("outcome", "awaiting").put("appVersion", 1)
        val events = history(); val next = JSONArray().put(event); for (i in 0 until minOf(events.length(), 499)) next.put(events.getJSONObject(i))
        p.edit().putString("events", next.toString()).putString("active_event_id", id).apply(); return id
    }
    fun completeActiveEvent(outcome: String) {
        val id = p.getString("active_event_id", null) ?: return; val events = history()
        for (i in 0 until events.length()) { val event = events.getJSONObject(i); if (event.optString("id") == id) { event.put("outcome", outcome).put("respondedAt", System.currentTimeMillis()).put("responseLatencyMs", System.currentTimeMillis() - event.optLong("firedAt")); break } }
        p.edit().putString("events", events.toString()).remove("active_event_id").apply()
    }
    fun history(): JSONArray = JSONArray(p.getString("events", "[]"))
    fun clearHistory() { p.edit().remove("events").remove("active_event_id").apply() }

    fun activeLabel(): String? = activePayload()?.audioLabel
    fun activeIsRetry(): Boolean = p.getBoolean("active_is_retry", false)
    fun clearActiveRetry() { p.edit().putBoolean("active_is_retry", false).apply() }
    fun setNextAt(value: Long) { p.edit().putLong("next_at", value).apply() }
    fun nextAt(): Long = p.getLong("next_at", 0)
    fun planDay(): String = p.getString("plan_day", "")!!
    fun remaining(): Int = p.getInt("remaining", 0)
    fun setPlan(day: String, count: Int) { p.edit().putString("plan_day", day).putInt("remaining", count).apply() }
    fun markFired() { if (p.getBoolean("snooze_pending", false)) { p.edit().putBoolean("snooze_pending", false).putBoolean("active_is_retry", true).apply(); return }; p.edit().putBoolean("active_is_retry", false).putInt("remaining", (remaining() - 1).coerceAtLeast(0)).putLong("last_fired", System.currentTimeMillis()).apply() }
    fun markSnooze() { p.edit().putBoolean("snooze_pending", true).apply() }
    fun lastFired(): Long = p.getLong("last_fired", 0)
    private fun jsonStrings(key: String): List<String> { val a = JSONArray(p.getString(key, "[]")); return (0 until a.length()).map { a.getString(it) } }
    private fun saveStrings(key: String, values: List<String>) { p.edit().putString(key, JSONArray(values).toString()).apply() }
}
