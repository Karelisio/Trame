package com.karelisio.trame.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

enum class ReleaseStatus {
    /** Prochaine sortie prévue dans le rythme habituel. */
    SCHEDULED,
    /** Un peu en retard sur le rythme : imminent. */
    LATE,
    /** Intervalle anormalement long : pause probable. */
    PAUSE_LIKELY,
    /** Série terminée. */
    ENDED,
}

data class ReleaseEstimate(
    val nextAt: Long,
    val nextChapter: String?,
    val intervalMs: Long,
    val lastReleaseAt: Long,
    val status: ReleaseStatus,
    /** 0..1 : régularité du rythme observé. */
    val confidence: Float,
) {
    val rhythmLabel: String
        get() {
            val days = intervalMs / DAY.toDouble()
            return when {
                days < 1.5 -> "Quotidien"
                days <= 8.5 -> "Hebdomadaire"
                days in 12.0..17.0 -> "Toutes les 2 semaines"
                days in 25.0..35.0 -> "Mensuel"
                else -> "Tous les ~${days.toInt()} jours"
            }
        }
}

const val HOUR = 3_600_000L
const val DAY = 24 * HOUR

/**
 * Estime la prochaine sortie à partir du rythme des derniers chapitres.
 * Les sorties groupées (plusieurs chapitres en moins de 24 h) comptent pour une seule.
 */
object ReleaseEstimator {
    fun estimate(
        releases: List<Pair<Double?, Long>>,
        seriesStatus: String? = null,
        now: Long = System.currentTimeMillis(),
        window: Int = 12,
    ): ReleaseEstimate? {
        val byChapter = releases.filter { it.second > 0 }
            .groupBy { it.first }
            .map { (n, l) -> n to l.minOf { it.second } }
            .sortedBy { it.second }
        if (byChapter.isEmpty()) return null

        val events = mutableListOf<Long>()
        byChapter.forEach { (_, t) -> if (events.isEmpty() || t - events.last() > DAY) events += t }
        val recent = events.takeLast(window + 1)
        val latestNumber = byChapter.mapNotNull { it.first }.maxOrNull()
        val nextNumber = latestNumber?.let { (kotlin.math.floor(it) + 1).toLong().toString() }
        val last = recent.last()

        if (seriesStatus == "completed") {
            return ReleaseEstimate(last, null, 0, last, ReleaseStatus.ENDED, 1f)
        }
        if (recent.size < 3) return null

        val intervals = recent.zipWithNext { a, b -> b - a }
        val median = intervals.median()
        if (median <= 0) return null
        // Régularité : écart absolu médian, en ignorant les pauses passées (> 2,5 × médiane).
        val regular = intervals.filter { it <= median * 2.5 }
        val mad = regular.map { abs(it - median) }.median()
        val confidence = (1f - (mad.toFloat() / median)).coerceIn(0f, 1f)

        val elapsed = now - last
        val pauseThreshold = max((median * 1.75).toLong(), median + 4 * DAY)
        val (next, status) = when {
            elapsed <= median -> last + median to ReleaseStatus.SCHEDULED
            elapsed > pauseThreshold -> last + ceil(elapsed / median.toDouble()).toLong() * median to ReleaseStatus.PAUSE_LIKELY
            else -> last + ceil(elapsed / median.toDouble()).toLong() * median to ReleaseStatus.LATE
        }
        return ReleaseEstimate(next, nextNumber, median, last, status, confidence)
    }

    private fun List<Long>.median(): Long {
        if (isEmpty()) return 0
        val s = sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
