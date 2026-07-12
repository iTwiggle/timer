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
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Random prompts",NotificationManager.IMPORTANCE_HIGH).apply { description="Your scheduled motivational ambushes" }) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) { DONE -> finishPrompt("done"); SKIP -> finishPrompt("skipped"); SNOOZE -> { finishPrompt("snoozed"); AlarmScheduler.scheduleNext(this,System.currentTimeMillis()+600_000) }; else -> playPrompt() }
        return START_NOT_STICKY
    }
    private fun playPrompt() {
        val prefs=AppPrefs(this); val clip=prefs.nextClip(); val label=clip?.name ?: "Random Chime"
        prefs.setActiveLabel(label); prefs.addHistory(label); startForeground(NOTIFICATION, notification(label))
        if(prefs.settings().vibrate) getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0,180,90,260),-1))
        val uri=clip?.let { Uri.parse(it.uri) } ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        player=MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setDataSource(this@PromptPlaybackService,uri); val volume=prefs.settings().volume/100f; setVolume(volume,volume)
            setOnPreparedListener { it.start() }; setOnCompletionListener { stopSelf() }; setOnErrorListener { _,_,_ -> stopSelf(); true }; prepareAsync()
        }
    }
    private fun notification(label: String): Notification {
        val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val done=PendingIntent.getService(this,2,Intent(this,PromptPlaybackService::class.java).setAction(DONE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val retry=AppPrefs(this).activeIsRetry()
        val secondaryAction=if(retry)SKIP else SNOOZE
        val secondaryLabel=if(retry)"Skip this prompt" else "Snooze 10 min"
        val secondary=PendingIntent.getService(this,3,Intent(this,PromptPlaybackService::class.java).setAction(secondaryAction),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("THE VOICE HAS SPOKEN").setContentText(label).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null,"DONE",done).build()).addAction(Notification.Action.Builder(null,secondaryLabel,secondary).build()).build()
    }
    private fun finishPrompt(outcome: String) { player?.stop(); player?.release(); player=null; val prefs=AppPrefs(this); prefs.activeLabel()?.let { prefs.addHistory(it,outcome) }; prefs.setActiveLabel(null); prefs.clearActiveRetry(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { player?.release(); super.onDestroy() }
}
