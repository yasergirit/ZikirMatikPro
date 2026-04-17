package com.yasergirit.zikirmasterpro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

class EzanService : Service() {

    companion object {
        const val CHANNEL_ID = "ezan_playback_channel"
        const val NOTIF_ID = 5001
        const val ACTION_STOP_EZAN = "com.yasergirit.zikirmasterpro.STOP_EZAN"
        private const val TAG = "EzanService"

        fun start(context: Context) {
            val intent = Intent(context, EzanService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EzanService::class.java))
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var volumeObserver: ContentObserver? = null

    // Telefonla etkileşim algılanınca ezanı hemen sustur.
    private val interactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    Log.d(TAG, "Stopping adhan due to phone interaction: ${intent.action}")
                    stopEzan()
                }
            }
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Başka uygulama ses aldı veya ses kesildi
                stopEzan()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Ses kısılabilir ama ezan için durdur
                stopEzan()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        registerInteractionReceiver()
        registerVolumeObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_EZAN) {
            stopEzan()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        startForeground(NOTIF_ID, notification)
        playEzan()
        return START_NOT_STICKY
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusListener)
        }
    }

    private fun playEzan() {
        try {
            mediaPlayer?.release()

            // Audio focus al - reddedilirse çalma
            if (!requestAudioFocus()) {
                Log.w(TAG, "Audio focus denied")
                stopEzan()
                return
            }

            // Ses seviyesini kontrol et - ses kapalıysa alarm stream üzerinden çal
            mediaPlayer = MediaPlayer.create(this, R.raw.ezan).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_ALARM) // ALARM stream - ses kapatma tuşundan etkilenmez
                        .build()
                )
                setOnCompletionListener {
                    stopEzan()
                }
                setOnErrorListener { _, _, _ ->
                    stopEzan()
                    true
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ezan playback error", e)
            stopEzan()
        }
    }

    private fun stopEzan() {
        mediaPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        mediaPlayer = null
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerInteractionReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(interactionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(interactionReceiver, filter)
        }
    }

    private fun registerVolumeObserver() {
        volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d(TAG, "Stopping adhan due to volume key interaction")
                stopEzan()
            }
        }

        try {
            val observer = volumeObserver ?: return
            contentResolver.registerContentObserver(
                Settings.System.getUriFor("volume_alarm"),
                false,
                observer
            )
            contentResolver.registerContentObserver(
                Settings.System.getUriFor("volume_music"),
                false,
                observer
            )
            contentResolver.registerContentObserver(
                Settings.System.getUriFor("volume_ring"),
                false,
                observer
            )
        } catch (e: Exception) {
            Log.w(TAG, "Volume observer could not be registered", e)
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
        val tapPendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Ezanı Kapat" butonu
        val stopIntent = Intent(this, EzanService::class.java).apply {
            action = ACTION_STOP_EZAN
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Ezan Sesi")
            .setContentText("Ezan çalınıyor...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(tapPendingIntent)
            .addAction(R.drawable.ic_notif, "Kapat", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        try { unregisterReceiver(interactionReceiver) } catch (_: Exception) {}
        try { volumeObserver?.let { contentResolver.unregisterContentObserver(it) } } catch (_: Exception) {}
        volumeObserver = null
        mediaPlayer?.let {
            try { it.release() } catch (_: Exception) {}
        }
        mediaPlayer = null
        abandonAudioFocus()
        super.onDestroy()
    }
}
