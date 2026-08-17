package com.kuyermqi.quotawidget.widget.usage

import androidx.compose.runtime.Composable
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.widget.contextString
import kotlin.math.roundToInt

/** Glance counterpart of the in-app reset countdown label ("x小时后重置"). */
@Composable
internal fun usageResetLabel(resetInSec: Long?): String {
    if (resetInSec == null || resetInSec < 0) {
        return contextString(R.string.usage_resets_unknown)
    }
    val totalMinutes = (resetInSec / 60.0).roundToInt().coerceAtLeast(0)
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60
    val text = when {
        days > 0 && hours > 0 -> "$days 天 $hours 小时"
        days > 0 -> "$days 天"
        hours > 0 && minutes > 0 -> "$hours 小时 $minutes 分"
        hours > 0 -> "$hours 小时"
        else -> "$minutes 分钟"
    }
    return contextString(R.string.usage_resets_in, text)
}
