package com.screenwise.app.util

/** All inputs are user-configured goals or counted completed actions. Not medical advice. */
object WellnessScore {
    data class Breakdown(val screen: Int, val limits: Int, val breaks: Int, val focus: Int, val bedtime: Int) {
        val total: Int get() = screen + limits + breaks + focus + bedtime
    }
    fun calculate(
        screenMinutes: Int, screenGoal: Int, exceededAppLimits: Int,
        completedBreaks: Int, plannedBreaks: Int, completedFocus: Int, bedtimeOnTrack: Boolean?
    ): Breakdown {
        val screenScore = when {
            screenGoal <= 0 -> 0
            screenMinutes <= screenGoal -> 35
            else -> (35.0 * screenGoal / screenMinutes.coerceAtLeast(1)).toInt().coerceIn(0, 35)
        }
        val limitScore = (25 - exceededAppLimits * 8).coerceIn(0, 25)
        val breakScore = if (plannedBreaks <= 0) 15 else (15.0 * completedBreaks / plannedBreaks).toInt().coerceIn(0, 15)
        val focusScore = if (completedFocus > 0) 15 else 0
        // If no bedtime observation exists, withhold the 10 points rather than assume success.
        return Breakdown(screenScore, limitScore, breakScore, focusScore, if (bedtimeOnTrack == true) 10 else 0)
    }
}
