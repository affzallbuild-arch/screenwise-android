package com.screenwise.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenwise.app.ScreenWiseApplication
import com.screenwise.app.data.*
import com.screenwise.app.usage.*
import com.screenwise.app.util.WellnessScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class ScreenWiseViewModel(application: Application): AndroidViewModel(application) {
    private val app = application as ScreenWiseApplication
    private val repo = UsageRepository(application)
    val settings = app.prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())
    val limits = app.database.dao().limits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = app.database.dao().categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val focusSessions = app.database.dao().focus().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val breakRecords = app.database.dao().breaks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val manualCalls = app.database.dao().calls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val permission = MutableStateFlow(repo.hasPermission())
    val today = MutableStateFlow(UsageSnapshot(0L, 0L, emptyList(), message = "Loading…"))
    val yesterday = MutableStateFlow(UsageSnapshot(0L, 0L, emptyList()))
    val report = MutableStateFlow(UsageSnapshot(0L, 0L, emptyList()))
    val rangeLabel = MutableStateFlow("Today")
    val updatedAt = MutableStateFlow(0L)
    val busy = MutableStateFlow(false)
    val dailySeries = MutableStateFlow<List<DayUsage>>(emptyList())
    val error = MutableStateFlow<String?>(null)

    init { refresh() }
    fun refresh() {
        viewModelScope.launch {
            val allowed = withContext(Dispatchers.IO) { repo.hasPermission() }
            permission.value = allowed
            if (allowed) {
                val result = withContext(Dispatchers.IO) { repo.today() to repo.yesterday() }
                today.value = result.first
                updatedAt.value = System.currentTimeMillis()
                yesterday.value = result.second
                if (rangeLabel.value == "Today") report.value = result.first
                dailySeries.value = withContext(Dispatchers.IO) { repo.dailySeries(7) }
            } else {
                dailySeries.value = emptyList()
                today.value = UsageSnapshot(0, 0, emptyList(), message = "Grant Usage Access to view statistics")
                report.value = today.value
            }
        }
    }
    fun changeRange(label: String) {
        rangeLabel.value = label
        viewModelScope.launch {
            busy.value = true
            try {
                val days = when (label) { "30 Days" -> 30; "7 Days" -> 7; else -> 7 }
                dailySeries.value = withContext(Dispatchers.IO) { repo.dailySeries(days) }
                report.value = withContext(Dispatchers.IO) { when (label) {
                    "Today" -> repo.today()
                    "Yesterday" -> repo.yesterday()
                    "7 Days" -> repo.range(7)
                    "30 Days" -> repo.range(30)
                    else -> repo.today()
                } }
            } catch (ex: Exception) { error.value = ex.localizedMessage ?: "Report unavailable" }
            finally { busy.value = false }
        }
    }
    fun custom(from: LocalDate, to: LocalDate) {
        if (from.isAfter(to)) { error.value = "Start date is after end date"; return }
        rangeLabel.value = "Custom"
        viewModelScope.launch {
            busy.value = true
            try { dailySeries.value = withContext(Dispatchers.IO) { repo.dailySeries(7) }
                report.value = withContext(Dispatchers.IO) {
                repo.snapshot(Dates.start(from), minOf(System.currentTimeMillis(), Dates.start(to.plusDays(1))), true)
            } } finally { busy.value = false }
        }
    }
    fun saveGoal(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) {
        viewModelScope.launch { app.prefs.int(key, value) }
    }
    fun saveString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        viewModelScope.launch { app.prefs.string(key, value) }
    }
    fun saveBool(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch { app.prefs.bool(key, value) }
    }
    fun setLimit(pkg: String, mins: Int, enabled: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) { app.database.dao().upsertLimit(AppLimit(pkg, mins.coerceAtLeast(1), enabled)) }
    }
    fun setCategory(pkg: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) { app.database.dao().upsertCategory(AppCategory(pkg, category)) }
    }
    fun recordBreak(minutes: Int) {
        viewModelScope.launch(Dispatchers.IO) { app.database.dao().addBreak(BreakRecord(completedAt = System.currentTimeMillis(), durationMinutes = minutes)) }
    }
    fun recordFocus(start: Long, planned: Int, mode: String, completed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            app.database.dao().addFocus(FocusSession(startedAt = start, endedAt = System.currentTimeMillis(), plannedMinutes = planned, mode = mode, completed = completed))
        }
    }
    fun manualCall(date: String, minutes: Int) {
        viewModelScope.launch(Dispatchers.IO) { app.database.dao().setCall(ManualCall(date, minutes)) }
    }
    fun resetData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dao = app.database.dao()
                dao.clearLimits(); dao.clearCategories(); dao.clearFocus(); dao.clearBreaks(); dao.clearCalls()
            }
            app.prefs.reset()
            refresh()
        }
    }
    fun score(): WellnessScore.Breakdown {
        val s = settings.value
        val used = today.value
        val todayDate = LocalDate.now()
        val doneBreaks = breakRecords.value.count {
            java.time.Instant.ofEpochMilli(it.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == todayDate
        }
        val doneFocus = focusSessions.value.count { it.completed &&
            java.time.Instant.ofEpochMilli(it.endedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == todayDate }
        val mapped = used.apps.map { app -> app.copy(category = categories.value.firstOrNull { it.packageName == app.packageName }?.category ?: app.category) }
        val categoryBreaches = listOf("Social Media" to s.socialGoal, "Gaming" to s.gameGoal).count { (category, goal) ->
            goal > 0 && mapped.filter { it.category == category }.sumOf { it.durationMs } / 60000 >= goal
        }
        val exceeded = limits.value.count { limit -> limit.enabled && (used.apps.firstOrNull { it.packageName == limit.packageName }?.durationMs ?: 0) / 60000 >= limit.minutes } + categoryBreaches
        val plannedBreaks = if (s.breakEvery > 0) ((used.totalMs / 60000) / s.breakEvery).toInt() else 0
        return WellnessScore.calculate((used.totalMs / 60000).toInt(), s.dailyGoal, exceeded, doneBreaks, plannedBreaks, doneFocus, null)
    }
}
