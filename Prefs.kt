package com.screenwise.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.screenwiseStore by preferencesDataStore("screenwise-settings")

data class UserSettings(
    val onboarded: Boolean = false,
    val dailyGoal: Int = 180,
    val socialGoal: Int = 60,
    val gameGoal: Int = 45,
    val breakEvery: Int = 45,
    val breakDuration: Int = 10,
    val bedtime: Int = 23,
    val notifications: Boolean = true,
    val quietStart: Int = 22,
    val quietEnd: Int = 8,
    val theme: String = "system",
    val language: String = "en",
    val retentionDays: Int = 30
)

class Prefs(private val context: Context) {
    companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val DAILY = intPreferencesKey("daily")
        val SOCIAL = intPreferencesKey("social")
        val GAME = intPreferencesKey("gaming")
        val BREAK_EVERY = intPreferencesKey("break_every")
        val BREAK_DURATION = intPreferencesKey("break_duration")
        val BEDTIME = intPreferencesKey("bedtime")
        val NOTIFY = booleanPreferencesKey("notifications")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val RETENTION = intPreferencesKey("retention")
    }
    val settings: Flow<UserSettings> = context.screenwiseStore.data.map { p ->
        UserSettings(
            p[ONBOARDED] ?: false, p[DAILY] ?: 180, p[SOCIAL] ?: 60,
            p[GAME] ?: 45, p[BREAK_EVERY] ?: 45, p[BREAK_DURATION] ?: 10,
            p[BEDTIME] ?: 23, p[NOTIFY] ?: true, p[QUIET_START] ?: 22,
            p[QUIET_END] ?: 8, p[THEME] ?: "system", p[LANGUAGE] ?: "en",
            p[RETENTION] ?: 30
        )
    }
    suspend fun current(): UserSettings = settings.first()
    suspend fun int(key: Preferences.Key<Int>, value: Int) = context.screenwiseStore.edit { it[key] = value }
    suspend fun string(key: Preferences.Key<String>, value: String) = context.screenwiseStore.edit { it[key] = value }
    suspend fun bool(key: Preferences.Key<Boolean>, value: Boolean) = context.screenwiseStore.edit { it[key] = value }
    suspend fun shouldAlertOnce(key: String, date: String): Boolean {
        val token = stringPreferencesKey("alert_" + key.take(130).replace(Regex("[^a-zA-Z0-9_.-]"), "_"))
        var should = false
        context.screenwiseStore.edit { store ->
            if (store[token] != date) { store[token] = date; should = true }
        }
        return should
    }
    suspend fun reset() { context.screenwiseStore.edit { it.clear() } }
}
