package com.screenwise.app.usage

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure calculations. Android event collection lives in UsageRepository. */
data class EventPoint(val at: Long, val packageName: String?, val action: Action)
enum class Action { OPEN, CLOSE, SCREEN_OFF }
data class UsageSession(val packageName: String, val start: Long, val end: Long) {
    val millis: Long get() = (end - start).coerceAtLeast(0)
}

object UsageMath {
    /** Lookback events may open sessions before the requested window. Results are clipped. */
    fun sessions(events: List<EventPoint>, from: Long, to: Long): List<UsageSession> {
        if (to <= from) return emptyList()
        val result = mutableListOf<UsageSession>()
        var activePackage: String? = null
        var activeFrom = 0L
        fun close(at: Long) {
            val pkg = activePackage ?: return
            val boundedEnd = minOf(at, to, activeFrom + 4 * 60 * 60 * 1000L)
            val boundedStart = maxOf(activeFrom, from)
            if (boundedEnd > boundedStart) result.add(UsageSession(pkg, boundedStart, boundedEnd))
            activePackage = null
        }
        for (e in events.sortedBy { it.at }) {
            if (e.at > to) break
            when (e.action) {
                Action.OPEN -> {
                    if (e.packageName.isNullOrBlank()) continue
                    if (activePackage != e.packageName) {
                        close(e.at)
                        activePackage = e.packageName
                        activeFrom = e.at
                    }
                }
                Action.CLOSE -> if (e.packageName == activePackage) close(e.at)
                Action.SCREEN_OFF -> close(e.at)
            }
        }
        close(to)
        return result
    }

    fun byApp(sessions: List<UsageSession>): Map<String, Long> = sessions
        .groupBy { it.packageName }.mapValues { (_, items) -> items.sumOf { it.millis } }

    fun hourly(sessions: List<UsageSession>, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        val hours = LongArray(24)
        sessions.forEach { session ->
            var current = session.start
            while (current < session.end) {
                val dateTime = Instant.ofEpochMilli(current).atZone(zone)
                val nextHour = dateTime.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
                val step = minOf(session.end, maxOf(current + 1, nextHour))
                hours[dateTime.hour] += step - current
                current = step
            }
        }
        return hours.toList()
    }
}
