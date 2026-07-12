package com.itwiggle.randomchime

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator

class PromptPlaybackService : Service() {
    companion object { const val PLAY="play"; const val DONE="done"; const val SNOOZE="snooze"; const val SKIP="skip"; const val CHANNEL="prompts"; const val NOTIFICATION=991 }
    private var player: MediaPlayer? = null
    private var scheduledAt: Long = 0
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Random prompts",NotificationManager.IMPORTANCE_HIGH).apply { description="Your scheduled motivational ambushes" }) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduledAt=intent?.getLongExtra("scheduled_at",System.currentTimeMillis())?:System.currentTimeMillis()
        when(intent?.action) { DONE -> finishPrompt("done"); SKIP -> finishPrompt("skipped"); SNOOZE -> { AppPrefs(this).prepareRetry(); finishPrompt("snoozed",true); AlarmScheduler.scheduleNext(this,System.currentTimeMillis()+600_000) }; else -> playPrompt() }
        return START_NOT_STICKY
    }
    private fun playPrompt() {
        val prefs=AppPrefs(this); val retry=prefs.activeIsRetry(); val saved=if(retry)prefs.retryPayload() else null
        val payload=saved ?: freshPayload(prefs.nextClip(),prefs.nextTextPrompt())
        prefs.setActivePayload(payload); prefs.startEvent(payload,scheduledAt,retry); startForeground(NOTIFICATION, notification(payload))
        if(prefs.settings().vibrate) getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0,180,90,260),-1))
        val uri=payload.audioUri?.let { Uri.parse(it) } ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        player=MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setDataSource(this@PromptPlaybackService,uri); val volume=prefs.settings().volume/100f; setVolume(volume,volume)
            setOnPreparedListener { it.start() }; setOnCompletionListener { stopSelf() }; setOnErrorListener { _,_,_ -> stopSelf(); true }; prepareAsync()
        }
    }
    private fun freshPayload(clip:Clip?,text:TextPrompt)=PromptPayload(clip?.uri,clip?.name?:"Random Chime",clip?.category?:"Prompt",text.id,text.text,text.category)
    private fun notification(payload: PromptPayload): Notification {
        val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val done=PendingIntent.getService(this,2,Intent(this,PromptPlaybackService::class.java).setAction(DONE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val retry=AppPrefs(this).activeIsRetry()
        val secondaryAction=if(retry)SKIP else SNOOZE
        val secondaryLabel=if(retry)"Skip this prompt" else "Snooze 10 min"
        val secondary=PendingIntent.getService(this,3,Intent(this,PromptPlaybackService::class.java).setAction(secondaryAction),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("AMBUSH · ${payload.audioCategory.uppercase()}").setContentText(payload.message).setSubText(payload.audioLabel).setStyle(Notification.BigTextStyle().bigText(payload.message)).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null,"DONE",done).build()).addAction(Notification.Action.Builder(null,secondaryLabel,secondary).build()).build()
    }
    private fun finishPrompt(outcome: String,preserveRetry:Boolean=false) { player?.stop(); player?.release(); player=null; val prefs=AppPrefs(this); prefs.completeActiveEvent(outcome); prefs.setActivePayload(null); prefs.clearActiveRetry(); if(!preserveRetry)prefs.clearRetryPayload(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { player?.release(); super.onDestroy() }
}
