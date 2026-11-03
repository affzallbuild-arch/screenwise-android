package com.screenwise.app.worker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.screenwise.app.MainActivity
import com.screenwise.app.ScreenWiseApplication
import com.screenwise.app.usage.UsageRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object AlertScheduler {
    private const val WORK_NAME = "screenwise_usage_alerts"
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
}

class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ScreenWiseApplication
        val settings = app.prefs.current()
        // Retention applies only to records created by ScreenWise, never Android's OS history.
        val cutoffDate = LocalDate.now().minusDays(settings.retentionDays.toLong().coerceAtLeast(1))
        val cutoff = cutoffDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dao = app.database.dao()
        dao.pruneFocus(cutoff)
        dao.pruneBreaks(cutoff)
        dao.pruneCalls(cutoffDate.toString())
        if (!settings.onboarded || !settings.notifications) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val hour = LocalTime.now().hour
        val isQuiet = if (settings.quietStart <= settings.quietEnd)
            hour in settings.quietStart until settings.quietEnd
        else hour >= settings.quietStart || hour < settings.quietEnd
        if (isQuiet) return Result.success()
        if (hour == (settings.bedtime + 22) % 24) {
            sendOnce(app, "bedtime", LocalDate.now().toString(), "Plan a calm evening",
                "Your chosen bedtime is approaching. Consider winding down.", 105)
        }
        val repo = UsageRepository(applicationContext)
        if (!repo.hasPermission()) return Result.success()
        val snapshot = repo.today()
        val today = LocalDate.now().toString()
        if (snapshot.totalMs / 60000 >= settings.dailyGoal && settings.dailyGoal > 0) {
            sendOnce(app, "daily", today, "Daily goal reached", "Your screen time is above your daily goal.", 101)
        }
        val social = snapshot.apps.filter { it.category == "Social Media" }.sumOf { it.durationMs } / 60000
        val gaming = snapshot.apps.filter { it.category == "Gaming" }.sumOf { it.durationMs } / 60000
        if (settings.socialGoal > 0 && social >= settings.socialGoal) {
            sendOnce(app, "social", today, "Social media goal reached", "You've reached your social media time goal.", 103)
        }
        if (settings.gameGoal > 0 && gaming >= settings.gameGoal) {
            sendOnce(app, "gaming", today, "Gaming goal reached", "You've reached your gaming time goal.", 104)
        }
        val appLimits = app.database.dao().activeLimits()
        appLimits.forEach { limit ->
            val used = snapshot.apps.firstOrNull { it.packageName == limit.packageName } ?: return@forEach
            if (used.durationMs / 60000 >= limit.minutes && limit.minutes > 0) {
                sendOnce(app, "limit_${limit.packageName}", today,
                    "App time limit reached", "${used.label} has reached your ${limit.minutes}-minute limit.",
                    200 + limit.packageName.hashCode().and(0x00ffffff))
            }
        }
        if (settings.breakEvery > 0 && snapshot.totalMs / 60000 >= settings.breakEvery) {
            sendOnce(app, "break", today, "Take a mindful break", "Consider a short screen break.", 102)
        }
        return Result.success()
    }
    private suspend fun sendOnce(app: ScreenWiseApplication, key: String, date: String,
        title: String, body: String, id: Int) {
        if (!app.prefs.shouldAlertOnce(key, date)) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("wellness", "Wellness reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(applicationContext, "wellness")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pending).setAutoCancel(true).build()
        manager.notify(id, notification)
    }
}
