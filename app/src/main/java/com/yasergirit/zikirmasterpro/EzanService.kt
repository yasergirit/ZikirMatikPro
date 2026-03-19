package com.yasergirit.zikirmasterpro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class EzanService : Service() {

    companion object {
        const val CHANNEL_ID = "ezan_playback_channel"
        const val NOTIF_ID = 3001
        private const val TAG = "EzanService"

        fun start(context: Context) {
            val intent = Intent(context, EzanService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIF_ID, notification)

        playEzan()

        return START_NOT_STICKY
    }

    private fun playEzan() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, R.raw.ezan).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    mediaPlayer = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    true
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ezan playback error", e)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ezan Sesi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ezan sesi çalınırken gösterilen bildirim"
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Ezan Sesi")
            .setContentText("Ezan çalınıyor...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
