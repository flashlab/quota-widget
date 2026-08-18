package com.kuyermqi.quotawidget.widget

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object WidgetDateFormatter {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")

    private val clockFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm")

    fun formatUpdatedAt(epochMs: Long): String {
        if (epochMs <= 0L) return "尚未更新"
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    /** Wall-clock time of day, e.g. 13:04. */
    fun formatClockTime(epochMs: Long): String {
        if (epochMs <= 0L) return "--:--"
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(clockFormatter)
    }
}
