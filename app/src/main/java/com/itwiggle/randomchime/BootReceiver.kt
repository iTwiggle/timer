package com.itwiggle.randomchime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(AppPrefs(context).settings().armed)AlarmScheduler.scheduleNext(context)}}
