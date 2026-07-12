package com.itwiggle.randomchime

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

data class Clip(val uri: String, var name: String, var category: String = "Motivation", var enabled: Boolean = true)
data class Settings(
    var start: String = "09:00", var end: String = "21:00", var exactMode: Boolean = false,
    var exactCount: Int = 2, var minCount: Int = 1, var maxCount: Int = 3, var minGap: Int = 60,
    var volume: Int = 70, var vibrate: Boolean = true, var shuffle: Boolean = true,
    var days: Set<Int> = (1..7).toSet(), var armed: Boolean = false
)

class AppPrefs(context:Context) {
    private val p=context.getSharedPreferences("random_chime",Context.MODE_PRIVATE)
    fun settings():Settings { val j=JSONObject(p.getString("settings","{}")!!);return Settings(
        j.optString("start","09:00"),j.optString("end","21:00"),j.optBoolean("exactMode",false),
        j.optInt("exactCount",2),j.optInt("minCount",1),j.optInt("maxCount",3),j.optInt("minGap",60),
        j.optInt("volume",70),j.optBoolean("vibrate",true),j.optBoolean("shuffle",true),
        j.optJSONArray("days")?.let{a->(0 until a.length()).map{a.getInt(it)}.toSet()}?: (1..7).toSet(),j.optBoolean("armed",false)) }
    fun save(s:Settings){val j=JSONObject().put("start",s.start).put("end",s.end).put("exactMode",s.exactMode).put("exactCount",s.exactCount).put("minCount",s.minCount).put("maxCount",s.maxCount).put("minGap",s.minGap).put("volume",s.volume).put("vibrate",s.vibrate).put("shuffle",s.shuffle).put("days",JSONArray(s.days.toList())).put("armed",s.armed);p.edit().putString("settings",j.toString()).apply()}
    fun clips():MutableList<Clip>{val a=JSONArray(p.getString("clips","[]"));return (0 until a.length()).map{a.getJSONObject(it)}.map{Clip(it.getString("uri"),it.optString("name","Prompt"),it.optString("category","Motivation"),it.optBoolean("enabled",true))}.toMutableList()}
    fun saveClips(items:List<Clip>){val a=JSONArray();items.forEach{a.put(JSONObject().put("uri",it.uri).put("name",it.name).put("category",it.category).put("enabled",it.enabled))};p.edit().putString("clips",a.toString()).apply()}
    fun nextClip():Clip?{val enabled=clips().filter{it.enabled};if(enabled.isEmpty())return null;val s=settings();if(!s.shuffle)return enabled.random();var deck=JSONArray(p.getString("deck","[]"));val valid=enabled.map{it.uri}.toSet();var ids=(0 until deck.length()).map{deck.getString(it)}.filter{it in valid}.toMutableList();if(ids.isEmpty())ids=enabled.map{it.uri}.shuffled().toMutableList();val id=ids.removeAt(0);deck=JSONArray(ids);p.edit().putString("deck",deck.toString()).apply();return enabled.firstOrNull{it.uri==id}}
    fun addHistory(label:String,outcome:String="awaiting"){val a=JSONArray(p.getString("history","[]"));val n=JSONArray().put(JSONObject().put("time",System.currentTimeMillis()).put("label",label).put("outcome",outcome));for(i in 0 until minOf(a.length(),39))n.put(a.getJSONObject(i));p.edit().putString("history",n.toString()).apply()}
    fun history():JSONArray=JSONArray(p.getString("history","[]"))
    fun clearHistory(){p.edit().remove("history").apply()}
    fun activeLabel():String?=p.getString("active_label",null)
    fun setActiveLabel(value:String?){p.edit().apply{if(value==null)remove("active_label") else putString("active_label",value)}.apply()}
    fun activeIsRetry():Boolean=p.getBoolean("active_is_retry",false)
    fun clearActiveRetry(){p.edit().putBoolean("active_is_retry",false).apply()}
    fun setNextAt(value:Long){p.edit().putLong("next_at",value).apply()}
    fun nextAt():Long=p.getLong("next_at",0)
    fun planDay():String=p.getString("plan_day","")!!
    fun remaining():Int=p.getInt("remaining",0)
    fun setPlan(day:String,count:Int){p.edit().putString("plan_day",day).putInt("remaining",count).apply()}
    fun markFired(){if(p.getBoolean("snooze_pending",false)){p.edit().putBoolean("snooze_pending",false).putBoolean("active_is_retry",true).apply();return};p.edit().putBoolean("active_is_retry",false).putInt("remaining",(remaining()-1).coerceAtLeast(0)).putLong("last_fired",System.currentTimeMillis()).apply()}
    fun markSnooze(){p.edit().putBoolean("snooze_pending",true).apply()}
    fun lastFired():Long=p.getLong("last_fired",0)
}
