package com.yasergirit.zikirmasterpro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d("BootReceiver", "Received ${intent.action}, scheduling prayer worker")
                schedulePrayerTimesWorker(context)
                runOnce(context)
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
        }

        fun runOnce(context: Context) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<PrayerTimesWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
