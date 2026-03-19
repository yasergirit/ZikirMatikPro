package com.yasergirit.zikirmasterpro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "prayer_times_channel"
        const val QUOTE_CHANNEL_ID = "daily_quote_prayer_channel"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_PRAYER_TIME = "prayer_time"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_IS_QUOTE = "is_quote"

        // Notification ID'leri (her vakit için farklı)
        const val NOTIF_ID_FAJR = 2001
        const val NOTIF_ID_SUNRISE = 2002
        const val NOTIF_ID_DHUHR = 2003
        const val NOTIF_ID_ASR = 2004
        const val NOTIF_ID_MAGHRIB = 2005
        const val NOTIF_ID_ISHA = 2006
        const val NOTIF_ID_QUOTE = 2010
    }

    override fun onReceive(context: Context, intent: Intent) {
        val isQuote = intent.getBooleanExtra(EXTRA_IS_QUOTE, false)

        if (isQuote) {
            if (!isQuoteEnabled(context)) return
            val pendingResult = goAsync()
            Thread {
                try {
                    val quote = fetchQuote()
                    if (quote != null) {
                        createQuoteChannel(context)
                        showQuoteNotification(context, quote)
                    }
                } catch (e: Exception) {
                    Log.e("PrayerAlarmReceiver", "Quote fetch error", e)
                } finally {
                    pendingResult.finish()
                }
            }.start()
            return
        }

        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return
        val prayerTime = intent.getStringExtra(EXTRA_PRAYER_TIME) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 2000)

        createNotificationChannel(context)
        showNotification(context, prayerName, prayerTime, notificationId)

        // Öğle vaktinde 3 saniye sonra ayet/hadis bildirimi gönder
        if (prayerName == "Öğle" && isQuoteEnabled(context)) {
            val pendingResult = goAsync()
            Thread {
                try {
                    Thread.sleep(3000)
                    val quote = fetchQuote()
                    if (quote != null) {
                        createQuoteChannel(context)
                        showQuoteNotification(context, quote)
                    }
                } catch (e: Exception) {
                    Log.e("PrayerAlarmReceiver", "Quote fetch error after Dhuhr", e)
                } finally {
                    pendingResult.finish()
                }
            }.start()
        }
    }

    private fun isQuoteEnabled(context: Context): Boolean {
        return try {
            val key = androidx.datastore.preferences.core.booleanPreferencesKey("quote_notif_enabled")
            kotlinx.coroutines.runBlocking {
                context.dataStore.data.first()[key] ?: true
            }
        } catch (e: Exception) { true }
    }

    private fun fetchQuote(): Pair<String, String>? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            // Alquran Cloud API - Diyanet Türkçe çevirisi, rastgele ayet (cache-bust ile her seferinde farklı)
            val request = Request.Builder()
                .url("https://api.alquran.cloud/v1/ayah/random/tr.diyanet?_=${System.currentTimeMillis()}")
                .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && body != null) {
                val json = JSONObject(body)
                val data = json.getJSONObject("data")
                val text = data.optString("text", "")
                val surah = data.getJSONObject("surah")
                val surahName = surah.optString("name", "")
                val surahNumber = surah.optInt("number", 0)
                val ayahInSurah = data.optInt("numberInSurah", 0)
                val source = "Kuran-ı Kerim • $surahName ($surahNumber:$ayahInSurah)"
                if (text.isNotEmpty()) Pair(text, source) else null
            } else null
        } catch (e: Exception) {
            Log.e("PrayerAlarmReceiver", "API error", e)
            null
        }
    }

    private fun createQuoteChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                QUOTE_CHANNEL_ID,
                "Günün Ayeti",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Öğle vakti ayet/hadis bildirimi"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showQuoteNotification(context: Context, quote: Pair<String, String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_quote_detail", true)
            putExtra("quote_text", quote.first)
            putExtra("quote_source", quote.second)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context, NOTIF_ID_QUOTE, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground)
            ?: BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val builder = NotificationCompat.Builder(context, QUOTE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setLargeIcon(largeIcon)
            .setContentTitle("Günün Ayeti")
            .setContentText(quote.first)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("Günün Ayeti")
                    .bigText(quote.first)
                    .setSummaryText(quote.second)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(tapPendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIF_ID_QUOTE, builder.build())
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ezanUri = Uri.parse("android.resource://${context.packageName}/${R.raw.ezan}")
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val name = "Namaz Vakitleri"
            val descriptionText = "Namaz vakti bildirimleri"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setSound(ezanUri, audioAttributes)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun playEzan(context: Context) {
        try {
            val isEzanEnabled = try {
                val key = androidx.datastore.preferences.core.booleanPreferencesKey("ezan_sound_enabled")
                kotlinx.coroutines.runBlocking {
                    context.dataStore.data.first()[key] ?: true
                }
            } catch (e: Exception) { true }

            if (!isEzanEnabled) return

            EzanService.start(context)
        } catch (e: Exception) {
            Log.e("PrayerAlarmReceiver", "Ezan service start error", e)
        }
    }

    private fun showNotification(context: Context, prayerName: String, prayerTime: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        // Ezan sesini çal
        playEzan(context)

        val title = "$prayerName Vakti"
        val text = if (prayerTime.isNotEmpty()) "$prayerName vakti girdi • $prayerTime" else "$prayerName vakti girdi"
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground)
            ?: BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val ezanUri = Uri.parse("android.resource://${context.packageName}/${R.raw.ezan}")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(ezanUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }
}
