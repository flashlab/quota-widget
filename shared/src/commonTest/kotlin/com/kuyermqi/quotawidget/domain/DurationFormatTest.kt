package com.kuyermqi.quotawidget.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DurationFormatTest {
    @Test
    fun remainingDuration_twoLargestUnits() {
        assertEquals("2d8h", formatRemainingDurationCompact(2 * 86_400L + 8 * 3_600L + 59L))
        assertEquals("3h14m", formatRemainingDurationCompact(3 * 3_600L + 14 * 60L + 59L))
        assertEquals("5m12s", formatRemainingDurationCompact(5 * 60L + 12L))
        assertEquals("0m0s", formatRemainingDurationCompact(0L))
        assertEquals("0m0s", formatRemainingDurationCompact(-30L))
        assertEquals("1d0h", formatRemainingDurationCompact(86_400L))
    }

    @Test
    fun elapsedDuration_singleLargestUnit() {
        assertEquals("2d", formatElapsedDurationCompact(2 * 86_400L + 8 * 3_600L))
        assertEquals("3h", formatElapsedDurationCompact(3 * 3_600L + 14 * 60L))
        assertEquals("14m", formatElapsedDurationCompact(14 * 60L + 30L))
        assertEquals("0m", formatElapsedDurationCompact(45L))
        assertEquals("0m", formatElapsedDurationCompact(-5L))
    }

    @Test
    fun liveReset_rebasesToNowAndClamps() {
        // Fetch at t=1000s said reset in 600s; at t=1300s -> 300s left.
        assertEquals(300L, liveResetInSec(600L, 1_000_000L, 1_300_000L))
        // Past reset clamps to zero.
        assertEquals(0L, liveResetInSec(600L, 1_000_000L, 2_000_000L))
        assertNull(liveResetInSec(null, 1_000_000L, 1_300_000L))
    }

    @Test
    fun liveElapsed_neverNegative() {
        assertEquals(300L, liveElapsedSec(1_000_000L, 1_300_000L))
        assertEquals(0L, liveElapsedSec(1_300_000L, 1_000_000L))
    }
}
