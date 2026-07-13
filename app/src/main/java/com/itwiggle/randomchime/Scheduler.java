package com.itwiggle.randomchime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class Scheduler {
    static void scheduleDay(Context c) {
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE); cancelAll(c);
        int count=Store.dailyCount(c), start=Store.p(c).getInt("start",8), end=Store.p(c).getInt("end",22);
        long dayStart=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long from=dayStart+start*3600000L, to=dayStart+end*3600000L; if(to<=from) to+=86400000L;
        long now=System.currentTimeMillis(); if(to<=now){from+=86400000L;to+=86400000L;} from=Math.max(from,now+3000);
        List<Long> times=new ArrayList<>(); Random r=new Random(); for(int i=0;i<count;i++) times.add(from+(long)(r.nextDouble()*Math.max(1,to-from))); Collections.sort(times);
        StringBuilder saved=new StringBuilder();
        for(int i=0;i<times.size();i++){ scheduleAt(c,times.get(i),1000+i); if(i>0)saved.append(','); saved.append(times.get(i)); }
        Store.p(c).edit().putBoolean("armed",true).putString("times",saved.toString()).apply();
    }

    static void scheduleAt(Context c,long at,int requestCode){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE); Intent i=new Intent(c,AlarmReceiver.class); i.putExtra("request",requestCode);
        PendingIntent pi=PendingIntent.getBroadcast(c,requestCode,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi); } catch(SecurityException e){ am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi); }
    }

    static void cancelAll(Context c){ AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE); for(int n=1000;n<1100;n++){PendingIntent pi=PendingIntent.getBroadcast(c,n,new Intent(c,AlarmReceiver.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE); if(pi!=null)am.cancel(pi);} Store.p(c).edit().putBoolean("armed",false).apply(); }
}
