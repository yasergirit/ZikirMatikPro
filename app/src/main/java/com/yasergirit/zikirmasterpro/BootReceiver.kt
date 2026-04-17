package com.yasergirit.zikirmasterpro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d("BootReceiver", "Received ${intent.action}, scheduling prayer worker")
                if (isPrayerNotificationsEnabled(context)) {
                    schedulePrayerTimesWorker(context)
                    runOnce(context)
                } else {
                    cancelPrayerTimesWorker(context)
                }
            }
        }
    }

    companion object {
        fun schedulePrayerTimesWorker(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<PrayerTimesWorker>(
                12, TimeUnit.HOURS
            ).setInitialDelay(5000L, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "prayer_times_work",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancelPrayerTimesWorker(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("prayer_times_work")
            PrayerTimesWorker.cancelAllAlarms(context)
        }

        fun runOnce(context: Context) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<PrayerTimesWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }

        private fun isPrayerNotificationsEnabled(context: Context): Boolean {
            return try {
                val key = booleanPreferencesKey("prayer_notif_enabled")
                kotlinx.coroutines.runBlocking {
                    context.dataStore.data.first()[key] ?: true
                }
            } catch (_: Exception) {
                true
            }
        }
    }
}
