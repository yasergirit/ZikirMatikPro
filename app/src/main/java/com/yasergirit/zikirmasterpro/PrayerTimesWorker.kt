package com.yasergirit.zikirmasterpro

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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

                // Önce HomeScreen'in kaydettiği vakitleri kontrol et (tek kaynak prensibi)
                val cachedTimes = getCachedPrayerTimes()
                val prayerTimes = if (cachedTimes != null) {
                    Log.d(TAG, "Using cached prayer times from HomeScreen: $cachedTimes")
                    cachedTimes
                } else {
                    // Cache yoksa API'den çek (ilk açılış veya cache süresi dolmuş)
                    val fetched = fetchPrayerTimes(lat, lng)
                    if (fetched == null) {
                        Log.e(TAG, "Could not fetch prayer times")
                        return@withContext Result.retry()
                    }
                    fetched
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

    private suspend fun getLastKnownLocation(): Pair<Double, Double>? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return null
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        // 1) Cache'li konum (hızlı)
        val cachedLocation = try {
            suspendCoroutine<Location?> { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { loc -> cont.resume(loc) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } catch (e: Exception) { null }

        // 2) LocationManager fallback
        val fallbackLocation = cachedLocation ?: try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) { null }

        // 3) Aktif konum (yavaş, son çare)
        val location = fallbackLocation ?: try {
            suspendCoroutine<Location?> { cont ->
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).addOnSuccessListener { loc -> cont.resume(loc) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } catch (e: Exception) { null }

        return if (location != null) Pair(location.latitude, location.longitude) else null
    }

    private suspend fun getStringPref(key: String, default: String): String {
        return try {
            val prefKey = stringPreferencesKey(key)
            context.dataStore.data.first()[prefKey] ?: default
        } catch (e: Exception) { default }
    }

    private suspend fun getCachedPrayerTimes(): Map<String, String>? {
        return try {
            val prefKey = stringPreferencesKey("cached_prayer_times")
            val json = context.dataStore.data.first()[prefKey] ?: return null
            val obj = JSONObject(json)
            val cachedDate = obj.getString("date")
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            if (cachedDate != today) return null // Eski tarihli cache'i kullanma
            mapOf(
                "Fajr" to obj.getString("Fajr"),
                "Sunrise" to obj.getString("Sunrise"),
                "Dhuhr" to obj.getString("Dhuhr"),
                "Asr" to obj.getString("Asr"),
                "Maghrib" to obj.getString("Maghrib"),
                "Isha" to obj.getString("Isha")
            )
        } catch (_: Exception) { null }
    }

    private suspend fun fetchPrayerTimes(lat: Double, lng: Double): Map<String, String>? {
        val method = getStringPref("prayer_method", "13")
        return try {
            val url = "https://islamicapi.com/api/v1/prayer-time/?lat=$lat&lon=$lng&method=$method&school=1&api_key=9ych8xGEPXNqi1SQHny2zXBJK34Jym1FAPdOpp7HLyW6qYgZ"

            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val json = JSONObject(body)
                val timings = json.getJSONObject("data").getJSONObject("times")

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

        // "Vakitten önce" bildirimleri için eşleme
        val beforeNotifIds = mapOf(
            "Fajr" to PrayerAlarmReceiver.NOTIF_ID_BEFORE_FAJR,
            "Sunrise" to PrayerAlarmReceiver.NOTIF_ID_BEFORE_SUNRISE,
            "Dhuhr" to PrayerAlarmReceiver.NOTIF_ID_BEFORE_DHUHR,
            "Asr" to PrayerAlarmReceiver.NOTIF_ID_BEFORE_ASR,
            "Maghrib" to PrayerAlarmReceiver.NOTIF_ID_BEFORE_MAGHRIB,
            "Isha" to PrayerAlarmReceiver.NOTIF_ID_BEFORE_ISHA,
        )
        val beforePrefKeys = mapOf(
            "Fajr" to "prayer_notif_before_fajr",
            "Sunrise" to "prayer_notif_before_sunrise",
            "Dhuhr" to "prayer_notif_before_dhuhr",
            "Asr" to "prayer_notif_before_asr",
            "Maghrib" to "prayer_notif_before_maghrib",
            "Isha" to "prayer_notif_before_isha",
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
            } catch (e: SecurityException) {
                Log.e(TAG, "Cannot schedule exact alarm for ${prayer.nameTr}", e)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                )
            }

            // ── "Vakitten önce" alarm (30 dk önce) ──
            val beforePrefKey = beforePrefKeys[prayer.apiKey]
            val beforeNotifId = beforeNotifIds[prayer.apiKey]
            if (beforePrefKey != null && beforeNotifId != null) {
                val beforeEnabled = getPref(beforePrefKey, false)
                val beforeIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME, prayer.nameTr)
                    putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_TIME, timeStr)
                    putExtra(PrayerAlarmReceiver.EXTRA_NOTIFICATION_ID, beforeNotifId)
                    putExtra(PrayerAlarmReceiver.EXTRA_IS_BEFORE, true)
                }
                val beforePendingIntent = PendingIntent.getBroadcast(
                    context, beforeNotifId, beforeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (!beforeEnabled) {
                    alarmManager.cancel(beforePendingIntent)
                } else {
                    val beforeCalendar = Calendar.getInstance().apply {
                        timeInMillis = calendar.timeInMillis
                        add(Calendar.MINUTE, -30) // 30 dakika önce
                    }
                    if (beforeCalendar.timeInMillis > System.currentTimeMillis()) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP, beforeCalendar.timeInMillis, beforePendingIntent
                                )
                            } else {
                                alarmManager.setAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP, beforeCalendar.timeInMillis, beforePendingIntent
                                )
                            }
                            Log.d(TAG, "${prayer.nameTr} BEFORE alarm set for 30min before $timeStr")
                        } catch (e: SecurityException) {
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP, beforeCalendar.timeInMillis, beforePendingIntent
                            )
                        }
                    }
                }
            }
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
            PrayerAlarmReceiver.NOTIF_ID_BEFORE_FAJR,
            PrayerAlarmReceiver.NOTIF_ID_BEFORE_SUNRISE,
            PrayerAlarmReceiver.NOTIF_ID_BEFORE_DHUHR,
            PrayerAlarmReceiver.NOTIF_ID_BEFORE_ASR,
            PrayerAlarmReceiver.NOTIF_ID_BEFORE_MAGHRIB,
            PrayerAlarmReceiver.NOTIF_ID_BEFORE_ISHA,
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
