package com.karelisio.trame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseEstimatorTest {
    private val t0 = 1_700_000_000_000L
    private fun weekly(count: Int, start: Double = 1100.0) = (0 until count).map { (start + it) to t0 + it * 7 * DAY }

    @Test fun weeklyScheduled() {
        val e = ReleaseEstimator.estimate(weekly(10), now = t0 + 9 * 7 * DAY + 2 * DAY)!!
        assertEquals(ReleaseStatus.SCHEDULED, e.status)
        assertEquals(t0 + 10 * 7 * DAY, e.nextAt)
        assertEquals("1110", e.nextChapter)
        assertEquals("Hebdomadaire", e.rhythmLabel)
    }

    @Test fun slightlyLate() {
        val e = ReleaseEstimator.estimate(weekly(10), now = t0 + 9 * 7 * DAY + 9 * DAY)!!
        assertEquals(ReleaseStatus.LATE, e.status)
        assertEquals(t0 + 11 * 7 * DAY, e.nextAt)
    }

    @Test fun pauseDetected() {
        val e = ReleaseEstimator.estimate(weekly(10), now = t0 + 9 * 7 * DAY + 20 * DAY)!!
        assertEquals(ReleaseStatus.PAUSE_LIKELY, e.status)
    }

    @Test fun bulkUploadsCountOnce() {
        val bulk = weekly(6) + listOf(1106.0 to t0 + 5 * 7 * DAY + HOUR)
        val e = ReleaseEstimator.estimate(bulk, now = t0 + 5 * 7 * DAY + DAY)
        assertNotNull(e)
        assertEquals(7 * DAY, e!!.intervalMs)
    }

    @Test fun notEnoughData() {
        assertNull(ReleaseEstimator.estimate(weekly(2), now = t0))
    }

    @Test fun ended() {
        assertEquals(ReleaseStatus.ENDED, ReleaseEstimator.estimate(weekly(3), "completed")!!.status)
    }
}
