package com.yasergirit.zikirmasterpro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            schedulePrayerTimesWorker(context)
        }
    }

    companion object {
        fun schedulePrayerTimesWorker(context: Context) {
            // İlk çalışmayı hemen yap, sonra her 12 saatte bir tekrarla
            // (günde 2 kez çalışarak vakitlerin güncel kalmasını sağla)
            val workRequest = PeriodicWorkRequestBuilder<PrayerTimesWorker>(
                12, TimeUnit.HOURS
            ).setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
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

        private fun calculateInitialDelay(): Long {
            // İlk çalışmayı mümkün olan en kısa sürede başlat (5 saniye sonra)
            return 5000L
        }

        fun runOnce(context: Context) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<PrayerTimesWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
