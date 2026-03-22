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
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "prayer_times_channel"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_PRAYER_TIME = "prayer_time"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_IS_BEFORE = "is_before"

        const val NOTIF_ID_FAJR = 2001
        const val NOTIF_ID_SUNRISE = 2002
        const val NOTIF_ID_DHUHR = 2003
        const val NOTIF_ID_ASR = 2004
        const val NOTIF_ID_MAGHRIB = 2005
        const val NOTIF_ID_ISHA = 2006

        // "Vakitten önce" notification ID'leri (4000+ to avoid EzanService collision)
        const val NOTIF_ID_BEFORE_FAJR = 4001
        const val NOTIF_ID_BEFORE_SUNRISE = 4002
        const val NOTIF_ID_BEFORE_DHUHR = 4003
        const val NOTIF_ID_BEFORE_ASR = 4004
        const val NOTIF_ID_BEFORE_MAGHRIB = 4005
        const val NOTIF_ID_BEFORE_ISHA = 4006

        private const val TAG = "PrayerAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return
        val prayerTime = intent.getStringExtra(EXTRA_PRAYER_TIME) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 2000)
        val isBefore = intent.getBooleanExtra(EXTRA_IS_BEFORE, false)

        createNotificationChannel(context)

        if (isBefore) {
            // 30 dk önce bildirimi: arka planda hadis çek
            val pendingResult = goAsync()
            Thread {
                try {
                    val hadith = fetchRandomShortHadith(context)
                    showBeforeNotification(context, prayerName, prayerTime, notificationId, hadith)
                } catch (e: Exception) {
                    Log.e(TAG, "Before notification error", e)
                    showBeforeNotification(context, prayerName, prayerTime, notificationId, null)
                } finally {
                    pendingResult.finish()
                }
            }.start()
            return
        }

        // Vaktinde bildirimi: arka planda ayet çek
        val pendingResult = goAsync()
        Thread {
            try {
                val quote = fetchRandomVerse()
                showNotification(context, prayerName, prayerTime, notificationId, quote)
            } catch (e: Exception) {
                Log.e(TAG, "Notification error", e)
                showNotification(context, prayerName, prayerTime, notificationId, null)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    // ── Rastgele kısa hadis çek (Nevevi 40 Hadis - kısa hadisler) ──

    private fun fetchRandomShortHadith(context: Context): Pair<String, String>? {
        return try {
            val lang = getLanguage(context)
            val langCode = when (lang) {
                "tr" -> "tur"
                "en" -> "eng"
                "de" -> "eng"
                "ar" -> "ara"
                else -> "tur"
            }
            val hadithNumber = (1..42).random()
            val url = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/$langCode-nawawi/$hadithNumber.min.json"

            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val json = JSONObject(body)
                val hadiths = json.getJSONArray("hadiths")
                if (hadiths.length() > 0) {
                    var text = hadiths.getJSONObject(0).getString("text")
                    // Çok uzunsa kısalt (bildirimde okunabilir olsun)
                    if (text.length > 300) text = text.substring(0, 297) + "..."
                    val source = when (lang) {
                        "en", "de" -> "40 Hadith an-Nawawi #$hadithNumber"
                        "ar" -> "الأربعون النووية #$hadithNumber"
                        else -> "40 Hadis (Nevevi) #$hadithNumber"
                    }
                    Pair(text, source)
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Hadith fetch error", e)
            null
        }
    }

    // ── Rastgele Kuran ayeti çek ──

    private fun fetchRandomVerse(): Pair<String, String>? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
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
            Log.e(TAG, "Verse fetch error", e)
            null
        }
    }

    private fun getLanguage(context: Context): String {
        return try {
            val key = androidx.datastore.preferences.core.stringPreferencesKey("selected_language")
            kotlinx.coroutines.runBlocking {
                context.dataStore.data.first()[key] ?: "tr"
            }
        } catch (_: Exception) { "tr" }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ezanUri = Uri.parse("android.resource://${context.packageName}/${R.raw.ezan}")
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val channel = NotificationChannel(CHANNEL_ID, "Namaz Vakitleri", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Namaz vakti bildirimleri"
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
            } catch (_: Exception) { true }

            if (!isEzanEnabled) return
            EzanService.start(context)
        } catch (e: Exception) {
            Log.e(TAG, "Ezan service start error", e)
        }
    }

    // ── 30 dk önce bildirimi (hadis ile) ──

    private fun showBeforeNotification(
        context: Context, prayerName: String, prayerTime: String,
        notificationId: Int, hadith: Pair<String, String>?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val lang = getLanguage(context)

        val title = when (lang) {
            "en" -> "30 minutes to $prayerName"
            "de" -> "30 Minuten bis $prayerName"
            "ar" -> "30 دقيقة حتى $prayerName"
            else -> "$prayerName vaktine 30 dakika kaldı"
        }

        val contentText = hadith?.first ?: when (lang) {
            "en" -> "$prayerName prayer time is at $prayerTime"
            "de" -> "$prayerName Gebetszeit ist um $prayerTime"
            "ar" -> "وقت صلاة $prayerName في $prayerTime"
            else -> "$prayerName vakti saat $prayerTime"
        }

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground)
            ?: BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300))

        if (hadith != null) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(hadith.first)
                    .setSummaryText(hadith.second)
            )
        }

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }

    // ── Vaktinde bildirimi (ezan + ayet) ──

    private fun showNotification(
        context: Context, prayerName: String, prayerTime: String,
        notificationId: Int, quote: Pair<String, String>?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        playEzan(context)

        val title = if (prayerTime.isNotEmpty()) "$prayerTime $prayerName Vakti" else "$prayerName Vakti"
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground)
            ?: BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val ezanUri = Uri.parse("android.resource://${context.packageName}/${R.raw.ezan}")

        val contentText = quote?.first ?: "$prayerName vakti girdi"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(ezanUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        if (quote != null) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(quote.first)
                    .setSummaryText(quote.second)
            )
        }

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }
}
