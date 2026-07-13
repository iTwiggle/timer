package com.itwiggle.randomchime;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    LinearLayout root, recordings, history; Switch random; EditText exact,min,max,start,end; TextView status;
    ActivityResultLauncher<String[]> picker;
    @Override public void onCreate(Bundle b){ super.onCreate(b); picker=registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(),this::importFiles); build(); requestPermissions(); refresh(); }
    TextView text(String s,int sp){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setPadding(4,12,4,12);return v;}
    EditText num(String hint,int value){EditText e=new EditText(this);e.setHint(hint);e.setInputType(2);e.setText(String.valueOf(value));return e;}
    Button button(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setOnClickListener(l);return b;}
    void build(){ ScrollView scroll=new ScrollView(this); root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,32,32,64);scroll.addView(root); TextView title=text("RANDOM CHIME",30);title.setTypeface(null,Typeface.BOLD);root.addView(title);root.addView(text("A prompt lands when it lands. Do the thing.",16));
        random=new Switch(this);random.setText("Random Range instead of Exact");random.setChecked(Store.p(this).getBoolean("random",false));root.addView(random);
        exact=num("Exact prompts/day",Store.p(this).getInt("exact",3));min=num("Minimum",Store.p(this).getInt("min",2));max=num("Maximum",Store.p(this).getInt("max",5));start=num("Window start hour (0–23)",Store.p(this).getInt("start",8));end=num("Window end hour (0–23)",Store.p(this).getInt("end",22));root.addView(exact);root.addView(min);root.addView(max);root.addView(start);root.addView(end);
        root.addView(button("IMPORT RECORDINGS",v->picker.launch(new String[]{"audio/*","audio/mpeg","audio/wav","audio/mp4","audio/ogg"})));root.addView(text("Recording deck",20));recordings=new LinearLayout(this);recordings.setOrientation(LinearLayout.VERTICAL);root.addView(recordings);
        status=text("",16);root.addView(status);root.addView(button("ARM RANDOM CHIMES",v->{save(); ensureExactAlarm(); Scheduler.scheduleDay(this);refresh();}));root.addView(button("STOP SCHEDULE",v->{Scheduler.cancelAll(this);refresh();}));root.addView(text("Accountability history",20));history=new LinearLayout(this);history.setOrientation(LinearLayout.VERTICAL);root.addView(history);setContentView(scroll); }
    void save(){Store.p(this).edit().putBoolean("random",random.isChecked()).putInt("exact",val(exact,3)).putInt("min",val(min,2)).putInt("max",val(max,5)).putInt("start",Math.min(23,val(start,8))).putInt("end",Math.min(23,val(end,22))).apply();}
    int val(EditText e,int d){try{return Integer.parseInt(e.getText().toString());}catch(Exception x){return d;}}
    void importFiles(List<Uri> uris){for(Uri u:uris){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}Store.addRecordings(this,uris);refresh();}
    void requestPermissions(){if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);}
    void ensureExactAlarm(){AlarmManager am=getSystemService(AlarmManager.class);if(android.os.Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms())startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getPackageName())));}
    void refresh(){recordings.removeAllViews();JSONArray a=Store.recordings(this);for(int i=0;i<a.length();i++){try{recordings.addView(text("• "+a.getJSONObject(i).optString("name","Recording"),15));}catch(Exception ignored){}}status.setText(Store.p(this).getBoolean("armed",false)?"ARMED — next day schedule is stored":"OFF — no random prompts scheduled");history.removeAllViews();try{JSONArray h=new JSONArray(Store.p(this).getString("history","[]"));for(int i=h.length()-1;i>=Math.max(0,h.length()-20);i--){history.addView(text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(h.getJSONObject(i).getLong("at")))+" — "+h.getJSONObject(i).getString("action"),14));}}catch(Exception ignored){}}
}
