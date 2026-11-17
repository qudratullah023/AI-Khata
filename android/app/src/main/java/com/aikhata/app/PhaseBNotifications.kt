package com.aikhata.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class PhaseBReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val shopId = ShopRegistry.activeShopId(applicationContext)
        val db = KhataDatabase(applicationContext, ShopRegistry.activeDatabaseName(applicationContext))
        val extra = PhaseBStore(applicationContext, shopId)
        try {
            val kind = inputData.getString("kind") ?: "weekly"
            val today = today()
            when (kind) {
                "daily" -> {
                    val totals = db.dailySummary(today)
                    notify(
                        "Daily shop summary",
                        "Sales: Rs. ${money(totals.first)} · Payments: Rs. ${money(totals.second)} · New dues: Rs. ${money(totals.third)}"
                    )
                }
                else -> {
                    val cutoff = dateDaysAgo(30)
                    val customers = db.overdueCustomerSummary(cutoff)
                    val suppliers = extra.overdueSupplierSummary(cutoff)
                    notify(
                        "Weekly overdue reminder",
                        "${customers.first + suppliers.first} accounts overdue · Total Rs. ${money(customers.second + suppliers.second)}"
                    )
                }
            }
        } finally {
            db.close()
            extra.close()
        }
        Result.success()
    }.getOrElse { Result.retry() }

    private fun notify(title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("ai_khata_phase_b", "AI Khata reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(applicationContext, "ai_khata_phase_b")
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(if (title.startsWith("Daily")) 7302 else 7301, notification) }
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun dateDaysAgo(days: Int): String {
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }
    private fun money(value: Double) = String.format(Locale.US, "%,.2f", value)
}

fun schedulePhaseBNotifications(context: Context, store: PhaseBStore) {
    val manager = WorkManager.getInstance(context)
    val weeklyEnabled = store.getSetting("weekly_reminder_enabled", "1") == "1"
    val dailyEnabled = store.getSetting("daily_summary_enabled", "1") == "1"
    manager.cancelUniqueWork("ai_khata_phase_b_weekly")
    manager.cancelUniqueWork("ai_khata_phase_b_daily")

    if (weeklyEnabled) {
        val day = store.getSetting("weekly_reminder_day", "1").toIntOrNull()?.coerceIn(1, 7) ?: 1
        val time = store.getSetting("weekly_reminder_time", "09:00")
        val delay = nextDelay(day, time)
        val request = PeriodicWorkRequestBuilder<PhaseBReminderWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(androidx.work.workDataOf("kind" to "weekly"))
            .build()
        manager.enqueueUniquePeriodicWork("ai_khata_phase_b_weekly", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    if (dailyEnabled) {
        val time = store.getSetting("daily_summary_time", "20:00")
        val delay = nextDelay(null, time)
        val request = PeriodicWorkRequestBuilder<PhaseBReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(androidx.work.workDataOf("kind" to "daily"))
            .build()
        manager.enqueueUniquePeriodicWork("ai_khata_phase_b_daily", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

private fun nextDelay(dayOfWeek: Int?, time: String): Long {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (dayOfWeek != null) {
            val androidDay = if (dayOfWeek == 7) Calendar.SUNDAY else dayOfWeek + 1
            while (get(Calendar.DAY_OF_WEEK) != androidDay || !after(now)) add(Calendar.DAY_OF_YEAR, 1)
        } else if (!after(now)) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
}
