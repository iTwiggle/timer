package com.itwiggle.randomchime

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator

class PromptPlaybackService:Service(){
    companion object{const val PLAY="play";const val DONE="done";const val SNOOZE="snooze";const val CHANNEL="prompts";const val NOTIFICATION=991}
    private var player:MediaPlayer?=null
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate(){super.onCreate();getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Random prompts",NotificationManager.IMPORTANCE_HIGH).apply{description="Your scheduled motivational ambushes"})}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{when(intent?.action){DONE->finish("done");SNOOZE->{finish("snoozed");AlarmScheduler.scheduleNext(this,System.currentTimeMillis()+10*60*1000)}else->play()} ;return START_NOT_STICKY}
    private fun play(){val prefs=AppPrefs(this);val clip=prefs.nextClip();val label=clip?.name?:"Random Chime";prefs.setActiveLabel(label);prefs.addHistory(label);startForeground(NOTIFICATION,notification(label));if(prefs.settings().vibrate)getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0,180,90,260),-1));if(clip==null){val tone=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);start(tone)}else start(Uri.parse(clip.uri))}
    private fun start(uri:Uri){player=MediaPlayer().apply{setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());setDataSource(this@PromptPlaybackService,uri);setVolume(AppPrefs(this@PromptPlaybackService).settings().volume/100f,AppPrefs(this@PromptPlaybackService).settings().volume/100f);setOnPreparedListener{it.start()};setOnCompletionListener{stopSelf()};setOnErrorListener{_,_,_->stopSelf();true};prepareAsync()}}
    private fun notification(label:String):Notification{val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);fun action(code:Int,name:String,verb:String)=Notification.Action.Builder(null,name,PendingIntent.getService(this,code,Intent(this,PromptPlaybackService::class.java).setAction(verb),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build();return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("THE VOICE HAS SPOKEN").setContentText(label).setContentIntent(open).setOngoing(true).addAction(action(2,"DONE",DONE)).addAction(action(3,"Snooze 10 min",SNOOZE)).build()}
    private fun finish(outcome:String){player?.stop();player?.release();player=null;val p=AppPrefs(this);p.activeLabel()?.let{p.addHistory(it,outcome)};p.setActiveLabel(null);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onDestroy(){player?.release();super.onDestroy()}
}
