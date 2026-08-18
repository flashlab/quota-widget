package com.kuyermqi.quotawidget.domain

/** Two largest adjacent units, no separator: 2d8h / 3h14m / 5m12s. */
fun formatRemainingDurationCompact(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val days = s / 86_400L
    val hours = (s % 86_400L) / 3_600L
    val minutes = (s % 3_600L) / 60L
    val seconds = s % 60L
    return when {
        days > 0L -> "${days}d${hours}h"
        hours > 0L -> "${hours}h${minutes}m"
        else -> "${minutes}m${seconds}s"
    }
}

/** Fetch-time reset countdown re-based to [nowEpochMs]; never negative. */
fun liveResetInSec(resetInSec: Long?, updatedAtEpochMs: Long, nowEpochMs: Long): Long? =
    resetInSec?.let { (it - (nowEpochMs - updatedAtEpochMs) / 1000L).coerceAtLeast(0L) }
