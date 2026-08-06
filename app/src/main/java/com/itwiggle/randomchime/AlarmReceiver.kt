package com.itwiggle.randomchime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val prefs=AppPrefs(context);val scheduledAt=prefs.nextAt();prefs.markFired();context.startForegroundService(Intent(context,PromptPlaybackService::class.java).setAction(PromptPlaybackService.PLAY).putExtra("scheduled_at",scheduledAt));AlarmScheduler.scheduleNext(context)}}
