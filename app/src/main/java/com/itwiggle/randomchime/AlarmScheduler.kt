package com.itwiggle.randomchime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.*
import kotlin.random.Random

object AlarmScheduler {
    private const val REQUEST=4401
    fun cancel(context:Context){val am=context.getSystemService(AlarmManager::class.java);am.cancel(intent(context));AppPrefs(context).setNextAt(0)}
    fun scheduleNext(context:Context, overrideAt:Long?=null):Long?{
        val prefs=AppPrefs(context);val s=prefs.settings();if(!s.armed&&overrideAt==null)return null
        if(overrideAt!=null)prefs.markSnooze();val at=overrideAt?:randomFuture(prefs,s)?:return null;val am=context.getSystemService(AlarmManager::class.java)
        if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms())am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent(context))
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent(context))
        prefs.setNextAt(at);return at
    }
    private fun intent(c:Context)=PendingIntent.getBroadcast(c,REQUEST,Intent(c,AlarmReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun randomFuture(prefs:AppPrefs,s:Settings):Long?{
        val now=ZonedDateTime.now();for(offset in 0..8){val day=now.toLocalDate().plusDays(offset.toLong());if(day.dayOfWeek.value !in s.days)continue
            if(prefs.planDay()==day.toString()&&prefs.remaining()<=0)continue
            if(prefs.planDay()!=day.toString()){val count=if(s.exactMode)s.exactCount else Random.nextInt(s.minCount,s.maxCount+1);prefs.setPlan(day.toString(),count)}
            val start=LocalTime.parse(s.start);val end=LocalTime.parse(s.end);var a=day.atTime(start).atZone(now.zone);var b=day.atTime(end).atZone(now.zone);if(!b.isAfter(a))b=b.plusDays(1)
            val spacing=if(prefs.planDay()==day.toString())prefs.lastFired()+s.minGap*60_000L else 0L
            val lower=maxOf(a.toInstant().toEpochMilli(),System.currentTimeMillis()+5000,spacing);val upper=b.toInstant().toEpochMilli();if(upper>lower)return Random.nextLong(lower,upper) else prefs.setPlan(day.toString(),0)
        };return null
    }
}
