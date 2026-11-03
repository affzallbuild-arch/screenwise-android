package com.screenwise.app.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Usage Access only. No accessibility, calls, contacts, location or network usage. */
data class DayUsage(val date: LocalDate, val totalMs: Long, val byApp: Map<String, Long>)

data class AppUsage(
    val packageName: String, val label: String, val durationMs: Long,
    val sessionCount: Int?, val category: String = "Other"
)
data class UsageSnapshot(
    val from: Long, val to: Long, val apps: List<AppUsage>,
    val sessions: List<UsageSession> = emptyList(),
    val hourlyMs: List<Long> = List(24) { 0L },
    val unlocks: Int? = null,
    val detailed: Boolean = false,
    val message: String = ""
) { val totalMs get() = apps.sumOf { it.durationMs } }

object Dates {
    fun start(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun today(): LocalDate = LocalDate.now()
}

class UsageRepository(private val context: Context) {
    private val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    fun hasPermission(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }
    private fun label(pkg: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg.substringAfterLast('.') }

    private fun categoryFor(pkg: String, label: String): String {
        val l = "$pkg $label".lowercase()
        return when {
            listOf("instagram", "facebook", "snapchat", "reddit", "twitter", "tiktok").any { it in l } -> "Social Media"
            listOf("youtube", "netflix", "music", "spotify", "primevideo").any { it in l } -> "Entertainment"
            listOf("game", "minecraft", "roblox", "pubg", "freefire").any { it in l } -> "Gaming"
            listOf("classroom", "duolingo", "learn", "education").any { it in l } -> "Education"
            listOf("whatsapp", "telegram", "signal", "dialer", "phone", "messages").any { it in l } -> "Communication"
            listOf("docs", "office", "calendar", "sheets", "gmail").any { it in l } -> "Productivity"
            else -> "Other"
        }
    }

    fun snapshot(from: Long, to: Long, aggregateOnly: Boolean = false): UsageSnapshot {
        if (!hasPermission()) return UsageSnapshot(from, to, emptyList(), message = "Usage Access is disabled")
        if (to <= from) return UsageSnapshot(from, to, emptyList(), message = "Invalid date range")
        if (!aggregateOnly) {
            try {
                // Up to 4h lookback makes midnight-crossing sessions visible when events remain available.
                val queryFrom = maxOf(0L, from - 4 * 60 * 60 * 1000L)
                val events = manager.queryEvents(queryFrom, to)
                if (events != null) {
                    val list = mutableListOf<EventPoint>()
                    var unlocks = 0
                    val e = UsageEvents.Event()
                    while (events.hasNextEvent()) {
                        events.getNextEvent(e)
                        when (e.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED -> list.add(EventPoint(e.timeStamp, e.packageName, Action.OPEN))
                            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                                list.add(EventPoint(e.timeStamp, e.packageName, Action.CLOSE))
                            UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                            UsageEvents.Event.DEVICE_SHUTDOWN -> list.add(EventPoint(e.timeStamp, null, Action.SCREEN_OFF))
                            UsageEvents.Event.KEYGUARD_HIDDEN -> if (e.timeStamp in from until to) unlocks++
                        }
                    }
                    val sessions = UsageMath.sessions(list, from, to)
                    if (sessions.isNotEmpty()) {
                        val totals = UsageMath.byApp(sessions)
                        val perApp = totals.map { (pkg, ms) ->
                            val name = label(pkg)
                            AppUsage(pkg, name, ms, sessions.count { it.packageName == pkg }, categoryFor(pkg, name))
                        }.sortedByDescending { it.durationMs }
                        return UsageSnapshot(from, to, perApp, sessions, UsageMath.hourly(sessions),
                            if (unlocks > 0) unlocks else null, true)
                    }
                }
            } catch (_: SecurityException) {
                return UsageSnapshot(from, to, emptyList(), message = "Usage Access was revoked")
            } catch (_: RuntimeException) { /* OS history not available; use coarse aggregates below */ }
        }
        // Aggregate fallback: timeline and session counts cannot be derived reliably.
        return try {
            val mapped = manager.queryAndAggregateUsageStats(from, to).values.mapNotNull { stats ->
                val ms = stats.totalTimeInForeground.coerceAtLeast(0)
                if (ms <= 0) null else {
                    val name = label(stats.packageName)
                    AppUsage(stats.packageName, name, ms, null, categoryFor(stats.packageName, name))
                }
            }.sortedByDescending { it.durationMs }
            UsageSnapshot(from, to, mapped, message = if (mapped.isEmpty()) "No usage data available for this range" else "Aggregate data: session timeline unavailable")
        } catch (_: Exception) {
            UsageSnapshot(from, to, emptyList(), message = "Android did not provide usage history")
        }
    }
    fun dailySeries(days: Int = 7): List<DayUsage> {
        if (!hasPermission()) return emptyList()
        return (days - 1 downTo 0).map { offset ->
            val day = LocalDate.now().minusDays(offset.toLong())
            val shot = snapshot(Dates.start(day), minOf(System.currentTimeMillis(), Dates.start(day.plusDays(1))),
                aggregateOnly = day != LocalDate.now())
            DayUsage(day, shot.totalMs, shot.apps.associate { it.packageName to it.durationMs })
        }
    }
    fun today(): UsageSnapshot = snapshot(Dates.start(Dates.today()), System.currentTimeMillis())
    fun yesterday(): UsageSnapshot {
        val day = Dates.today().minusDays(1)
        return snapshot(Dates.start(day), Dates.start(day.plusDays(1)))
    }
    fun range(days: Long): UsageSnapshot = snapshot(Dates.start(Dates.today().minusDays(days - 1)), System.currentTimeMillis(), days > 2)
    fun day(date: LocalDate): UsageSnapshot = snapshot(
        Dates.start(date), minOf(Dates.start(date.plusDays(1)), System.currentTimeMillis()), date != Dates.today()
    )
}
