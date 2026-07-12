package com.itwiggle.randomchime

import android.Manifest
import android.app.*
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
import android.view.*
import android.widget.*
import org.json.JSONArray
import java.text.DateFormat
import java.util.*

class MainActivity:Activity(){
    private lateinit var prefs:AppPrefs;private lateinit var root:LinearLayout;private var config=Settings();private var clips=mutableListOf<Clip>()
    private val green=Color.rgb(31,107,79);private val paper=Color.rgb(251,252,248);private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt();private val pickAudio=41
    override fun onCreate(state:Bundle?){super.onCreate(state);prefs=AppPrefs(this);config=prefs.settings();clips=prefs.clips();requestPermissions();draw()}
    override fun onResume(){super.onResume();if(::prefs.isInitialized){config=prefs.settings();draw()}}
    private fun requestPermissions(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),7);val am=getSystemService(AlarmManager::class.java);if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms())startActivity(Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))}
    private fun card():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(20),dp(20),dp(20));setBackgroundColor(paper)}
    private fun text(value:String,size:Float=14f,bold:Boolean=false)=TextView(this).apply{this.text=value;textSize=size;setTextColor(Color.rgb(23,32,29));if(bold)setTypeface(typeface,1);setPadding(0,dp(5),0,dp(5))}
    private fun button(label:String,primary:Boolean=true,onClick:()->Unit)=Button(this).apply{text=label;isAllCaps=false;setTextColor(if(primary)Color.WHITE else green);setBackgroundColor(if(primary)green else Color.WHITE);setOnClickListener{onClick()}}
    private fun section(title:String)=text(title,23f,true)
    private fun spacer()=Space(this).apply{minimumHeight=dp(14)}
    private fun row(vararg views:View)=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;views.forEach{addView(it,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{marginEnd=dp(6)})}}
    private fun number(label:String,value:Int,onChange:(Int)->Unit)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(text(label,12f,true));addView(EditText(this@MainActivity).apply{inputType=2;setText(value.toString());setOnFocusChangeListener{_,focus->if(!focus)onChange(text.toString().toIntOrNull()?:value)}})}
    private fun time(label:String,value:String,onChange:(String)->Unit)=button("$label  $value",false){val parts=value.split(":");TimePickerDialog(this,{_,h,m->onChange("%02d:%02d".format(h,m));saveAndDraw()},parts[0].toInt(),parts[1].toInt(),false).show()}
    private fun saveAndDraw(){prefs.save(config);draw()}
    private fun draw(){root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(24),dp(16),dp(50));setBackgroundColor(Color.rgb(238,241,233))};val scroll=ScrollView(this).apply{addView(root)};setContentView(scroll)
        root.addView(text("RANDOM CHIME",11f,true));root.addView(text("Let the day surprise you.",30f,true));root.addView(spacer())
        val status=card();status.gravity=Gravity.CENTER;status.addView(text(if (config.armed) "ARMED" else "QUIET",12f,true));val next=prefs.nextAt();status.addView(text(if(next>System.currentTimeMillis())DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(next)) else "Waiting to be armed",25f,true));status.addView(text(if(config.armed)"Android owns the alarm now—even while closed." else "Your settings and audio deck are stored on this phone",12f));root.addView(status)
        root.addView(row(button(if (config.armed) "Disarm" else "Arm random prompts"){config.armed=!config.armed;prefs.save(config);if(config.armed)AlarmScheduler.scheduleNext(this) else AlarmScheduler.cancel(this);draw()},button("Test in 10 sec",false){config.armed=true;prefs.save(config);AlarmScheduler.scheduleNext(this,System.currentTimeMillis()+10000);draw()}));root.addView(spacer())
        val schedule=card();schedule.addView(section("Your surprise window"));schedule.addView(row(time("Start",config.start){config.start=it},time("End",config.end){config.end=it}));schedule.addView(row(button(if (config.exactMode) "✓ Exact number" else "Exact number",!config.exactMode){config.exactMode=true;saveAndDraw()},button(if (!config.exactMode) "✓ Random range" else "Random range",config.exactMode){config.exactMode=false;saveAndDraw()}));if(config.exactMode)schedule.addView(number("Prompts per day",config.exactCount){config.exactCount=it.coerceIn(1,12);prefs.save(config)})else schedule.addView(row(number("At least",config.minCount){config.minCount=it.coerceIn(1,config.maxCount);prefs.save(config)},number("At most",config.maxCount){config.maxCount=it.coerceIn(config.minCount,12);prefs.save(config)}));schedule.addView(text("Minimum spacing: ${config.minGap} minutes",12f,true));schedule.addView(SeekBar(this).apply{max=47;progress=(config.minGap/5-1).coerceIn(0,47);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,p:Int,u:Boolean){config.minGap=(p+1)*5}override fun onStartTrackingTouch(s:SeekBar?){}override fun onStopTrackingTouch(s:SeekBar?){prefs.save(config);draw()}})});root.addView(schedule);root.addView(spacer())
        val deck=card();deck.addView(section("Your voices of consequence"));deck.addView(button("＋ Add recordings"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="audio/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},pickAudio)});deck.addView(text("${clips.count{it.enabled}} active · shuffle without repeats",12f));if(clips.isEmpty())deck.addView(text("No recordings yet. Android's alarm tone is the fallback."))else clips.forEachIndexed{i,c->deck.addView(row(button("▶",false){play(c)},LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(EditText(this@MainActivity).apply{setText(c.name);setSingleLine();setOnFocusChangeListener{_,f->if(!f){c.name=text.toString();prefs.saveClips(clips)}});addView(EditText(this@MainActivity).apply{setText(c.category);setSingleLine();textSize=11f;setOnFocusChangeListener{_,f->if(!f){c.category=text.toString();prefs.saveClips(clips)}})},button(if (c.enabled) "ON" else "OFF",false){c.enabled=!c.enabled;prefs.saveClips(clips);draw()},button("×",false){clips.removeAt(i);prefs.saveClips(clips);draw()}))};root.addView(deck);root.addView(spacer())
        prefs.activeLabel()?.let{label->val verdict=card();verdict.gravity=Gravity.CENTER;verdict.addView(text("THE VOICE HAS SPOKEN",11f,true));verdict.addView(text("So—did you do it?",27f,true));verdict.addView(text(label));verdict.addView(button("✓ DONE"){sendService(PromptPlaybackService.DONE);draw()});verdict.addView(button("Snooze 10 min — excuses > motivation",false){sendService(PromptPlaybackService.SNOOZE);draw()});root.addView(verdict);root.addView(spacer())}
        val history=card();history.addView(section("Recent prompts"));val h=prefs.history();if(h.length()==0)history.addView(text("No prompts yet."))else for(i in 0 until minOf(h.length(),8)){val j=h.getJSONObject(i);history.addView(text("${if(j.optString("outcome")=="done")"✓" else if(j.optString("outcome")=="snoozed")"↻" else "♪"}  ${j.getString("label")} · ${j.optString("outcome")}"))};history.addView(button("Clear history",false){prefs.clearHistory();draw()});root.addView(history)
    }
    private fun sendService(action:String){startService(Intent(this,PromptPlaybackService::class.java).setAction(action))}
    @Deprecated("Legacy picker retained to avoid an additional UI dependency")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=pickAudio||resultCode!=RESULT_OK||data==null)return;val uris=mutableListOf<Uri>();data.data?.let{uris.add(it)};data.clipData?.let{c->for(i in 0 until c.itemCount)uris.add(c.getItemAt(i).uri)};uris.distinct().forEach{uri->try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};if(clips.none{it.uri==uri.toString()})clips.add(Clip(uri.toString(),displayName(uri)))};prefs.saveClips(clips);draw()}
    private fun play(c:Clip){android.media.MediaPlayer.create(this,Uri.parse(c.uri))?.start()}
    private fun displayName(uri:Uri):String{contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())return it.getString(0).substringBeforeLast('.')};return "Prompt"}
}
