package com.itwiggle.randomchime;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class PlaybackService extends Service {
    private static final String CHANNEL="random_chime_prompts"; private MediaPlayer player;
    @Override public void onCreate(){ super.onCreate(); NotificationManager nm=getSystemService(NotificationManager.class); nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Motivation prompts",NotificationManager.IMPORTANCE_HIGH)); }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        Intent done=new Intent(this,ActionReceiver.class).setAction("DONE"), snooze=new Intent(this,ActionReceiver.class).setAction("SNOOZE");
        PendingIntent dpi=PendingIntent.getBroadcast(this,1,done,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE), spi=PendingIntent.getBroadcast(this,2,snooze,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Random Chime").setContentText("Your prompt picked you. What are you doing with it?").setPriority(NotificationCompat.PRIORITY_HIGH).setOngoing(true).addAction(0,"DONE",dpi).addAction(0,"Snooze 10 min — excuses > motivation",spi).build();
        startForeground(42,n); Uri uri=Store.nextRecording(this); if(uri!=null){ try{ if(player!=null)player.release(); player=new MediaPlayer(); player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()); player.setDataSource(this,uri); player.setOnPreparedListener(MediaPlayer::start); player.prepareAsync(); }catch(Exception ignored){} }
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){ if(player!=null){player.stop();player.release();player=null;} super.onDestroy(); }
    @Override public IBinder onBind(Intent i){return null;}
}
