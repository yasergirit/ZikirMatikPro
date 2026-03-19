package com.yasergirit.zikirmasterpro

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

class PrayerTimesWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "PrayerTimesWorker"
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                // Ana toggle kapalıysa çalışma
                val enabled = getPref("prayer_notif_enabled", true)
                if (!enabled) {
                    Log.d(TAG, "Prayer notifications disabled")
                    cancelAllAlarms()
                    return@withContext Result.success()
                }

                // Konum al
                val location = getLastKnownLocation()
                if (location == null) {
                    Log.e(TAG, "Could not get location")
                    return@withContext Result.retry()
                }

                val (lat, lng) = location
                Log.d(TAG, "Location: $lat, $lng")

                // API'den namaz vakitlerini çek
                val prayerTimes = fetchPrayerTimes(lat, lng)
                if (prayerTimes == null) {
                    Log.e(TAG, "Could not fetch prayer times")
                    return@withContext Result.retry()
                }

                Log.d(TAG, "Prayer times: $prayerTimes")

                // Alarmları kur
                scheduleAlarms(prayerTimes)

                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Worker error", e)
                Result.retry()
            }
        }
    }

    private suspend fun getPref(key: String, default: Boolean): Boolean {
        return try {
            val prefKey = booleanPreferencesKey(key)
            context.dataStore.data.first()[prefKey] ?: default
        } catch (e: Exception) {
            default
        }
    }

    private fun getLastKnownLocation(): Pair<Double, Double>? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // GPS'ten dene
        val gpsLocation = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) { null }

        // Network'ten dene
        val networkLocation = try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) { null }

        val location = gpsLocation ?: networkLocation
        return if (location != null) Pair(location.latitude, location.longitude) else null
    }

    private fun fetchPrayerTimes(lat: Double, lng: Double): Map<String, String>? {
        return try {
            val timestamp = System.currentTimeMillis() / 1000
            val url = "https://api.aladhan.com/v1/timings/$timestamp?latitude=$lat&longitude=$lng&method=13"

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val json = JSONObject(body)
                val timings = json.getJSONObject("data").getJSONObject("timings")

                mapOf(
                    "Fajr" to timings.getString("Fajr"),
                    "Sunrise" to timings.getString("Sunrise"),
                    "Dhuhr" to timings.getString("Dhuhr"),
                    "Asr" to timings.getString("Asr"),
                    "Maghrib" to timings.getString("Maghrib"),
                    "Isha" to timings.getString("Isha")
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "API error", e)
            null
        }
    }

    private suspend fun scheduleAlarms(prayerTimes: Map<String, String>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        data class PrayerInfo(
            val apiKey: String,
            val prefKey: String,
            val nameTr: String,
            val notifId: Int
        )

        val prayers = listOf(
            PrayerInfo("Fajr", "prayer_notif_fajr", "İmsak", PrayerAlarmReceiver.NOTIF_ID_FAJR),
            PrayerInfo("Sunrise", "prayer_notif_sunrise", "Güneş", PrayerAlarmReceiver.NOTIF_ID_SUNRISE),
            PrayerInfo("Dhuhr", "prayer_notif_dhuhr", "Öğle", PrayerAlarmReceiver.NOTIF_ID_DHUHR),
            PrayerInfo("Asr", "prayer_notif_asr", "İkindi", PrayerAlarmReceiver.NOTIF_ID_ASR),
            PrayerInfo("Maghrib", "prayer_notif_maghrib", "Akşam", PrayerAlarmReceiver.NOTIF_ID_MAGHRIB),
            PrayerInfo("Isha", "prayer_notif_isha", "Yatsı", PrayerAlarmReceiver.NOTIF_ID_ISHA),
        )

        for (prayer in prayers) {
            val timeStr = prayerTimes[prayer.apiKey] ?: continue
            val enabled = getPref(prayer.prefKey, true)

            val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME, prayer.nameTr)
                putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_TIME, timeStr)
                putExtra(PrayerAlarmReceiver.EXTRA_NOTIFICATION_ID, prayer.notifId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                prayer.notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (!enabled) {
                alarmManager.cancel(pendingIntent)
                Log.d(TAG, "${prayer.nameTr} disabled, alarm cancelled")
                continue
            }

            // Saat ve dakika parse et (format: "HH:mm" veya "HH:mm (TRT)")
            val cleanTime = timeStr.split(" ")[0] // "(TRT)" gibi suffix'leri temizle
            val parts = cleanTime.split(":")
            if (parts.size < 2) continue

            val hour = parts[0].toIntOrNull() ?: continue
            val minute = parts[1].toIntOrNull() ?: continue

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Vakit geçtiyse alarm kurma (bugün için)
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                Log.d(TAG, "${prayer.nameTr} ($timeStr) already passed today, skipping")
                continue
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                    )
                }
                Log.d(TAG, "${prayer.nameTr} alarm set for $timeStr")

                // Öğle vaktinden 3 saniye sonra ayet/hadis bildirimi kur
                if (prayer.apiKey == "Dhuhr") {
                    scheduleQuoteAlarm(alarmManager, calendar.timeInMillis + 3000)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Cannot schedule exact alarm for ${prayer.nameTr}", e)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                )
            }
        }
    }

    private fun scheduleQuoteAlarm(alarmManager: AlarmManager, triggerAtMillis: Long) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra(PrayerAlarmReceiver.EXTRA_IS_QUOTE, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PrayerAlarmReceiver.NOTIF_ID_QUOTE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.d(TAG, "Quote alarm set for 3s after Dhuhr")
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAllAlarms() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notifIds = listOf(
            PrayerAlarmReceiver.NOTIF_ID_FAJR,
            PrayerAlarmReceiver.NOTIF_ID_SUNRISE,
            PrayerAlarmReceiver.NOTIF_ID_DHUHR,
            PrayerAlarmReceiver.NOTIF_ID_ASR,
            PrayerAlarmReceiver.NOTIF_ID_MAGHRIB,
            PrayerAlarmReceiver.NOTIF_ID_ISHA,
        )
        for (id in notifIds) {
            val intent = Intent(context, PrayerAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
