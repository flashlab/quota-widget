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

/** Single largest unit: 2d / 3h / 14m; sub-minute rounds to 0m. */
fun formatElapsedDurationCompact(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val days = s / 86_400L
    val hours = s / 3_600L
    val minutes = s / 60L
    return when {
        days > 0L -> "${days}d"
        hours > 0L -> "${hours}h"
        minutes > 0L -> "${minutes}m"
        else -> "0m"
    }
}

/** Fetch-time reset countdown re-based to [nowEpochMs]; never negative. */
fun liveResetInSec(resetInSec: Long?, updatedAtEpochMs: Long, nowEpochMs: Long): Long? =
    resetInSec?.let { (it - (nowEpochMs - updatedAtEpochMs) / 1000L).coerceAtLeast(0L) }

/** Seconds since [updatedAtEpochMs]; never negative. */
fun liveElapsedSec(updatedAtEpochMs: Long, nowEpochMs: Long): Long =
    ((nowEpochMs - updatedAtEpochMs) / 1000L).coerceAtLeast(0L)
