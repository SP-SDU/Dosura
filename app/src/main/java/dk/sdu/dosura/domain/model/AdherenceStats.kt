package dk.sdu.dosura.domain.model

data class AdherenceStats(
    val totalScheduled: Int,
    val totalTaken: Int,
    val totalMissed: Int,
    val totalSkipped: Int,
    val adherencePercentage: Float,
    val weeklyStreak: Int,
    val currentStreak: Int
) {
    companion object {
        fun empty() = AdherenceStats(
            totalScheduled = 0,
            totalTaken = 0,
            totalMissed = 0,
            totalSkipped = 0,
            adherencePercentage = 0f,
            weeklyStreak = 0,
            currentStreak = 0
        )
    }
}
