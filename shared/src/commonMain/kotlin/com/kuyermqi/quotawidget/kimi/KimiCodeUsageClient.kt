package com.kuyermqi.quotawidget.kimi

import com.kuyermqi.quotawidget.codex.classifyWindow
import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Kimi Code (Coding Plan) usage API.
 * `GET https://api.kimi.com/coding/v1/usages` with a `sk-kimi-` Bearer key.
 * Numeric fields arrive as JSON strings ("used": "32"); resetTime is ISO-8601 UTC.
 */
class KimiCodeUsageClient(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun fetchUsage(
        apiKey: String,
        nowEpochMs: Long = currentTimeMillis(),
    ): QuotaSnapshot {
        val response = try {
            httpClient.get(USAGE_URL) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                accept(ContentType.Application.Json)
            }
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                    throw SessionExpiredException("Kimi Code API Key 无效或已失效，请检查配置")
                HttpStatusCode.TooManyRequests ->
                    throw IllegalStateException("Kimi Code API 请求过于频繁，请稍后重试", e)
                else ->
                    throw IllegalStateException("查询 Kimi Code 额度失败: ${e.response.status}", e)
            }
        }
        val body = response.bodyAsText()
        if (body.isBlank()) {
            throw IllegalStateException("Kimi Code 返回为空")
        }
        val dto = try {
            json.decodeFromString(KimiCodeUsageResponse.serializer(), body)
        } catch (e: Exception) {
            throw IllegalStateException("Kimi Code 返回无法解析", e)
        }
        val snapshot = toSnapshot(dto, updatedAtEpochMs = nowEpochMs, nowEpochMs = nowEpochMs)
        if (snapshot.windows.isEmpty()) {
            throw IllegalStateException("无法解析 Kimi Code 额度窗口")
        }
        return snapshot
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        const val USAGE_URL = "https://api.kimi.com/coding/v1/usages"

        fun toSnapshot(
            dto: KimiCodeUsageResponse,
            updatedAtEpochMs: Long,
            nowEpochMs: Long = updatedAtEpochMs,
        ): QuotaSnapshot {
            val mapped = linkedMapOf<QuotaWindowKind, QuotaWindow>()
            dto.usage?.toQuotaWindow(nowEpochMs)?.let { mapped[QuotaWindowKind.WEEKLY] = it }
            for (entry in dto.limits) {
                val seconds = entry.window?.durationSeconds() ?: continue
                val kind = classifyWindow(seconds) ?: continue
                val window = entry.detail?.toQuotaWindow(nowEpochMs) ?: continue
                mapped[kind] = window.copy(kind = kind)
            }
            val windows = listOfNotNull(
                mapped[QuotaWindowKind.FIVE_HOUR],
                mapped[QuotaWindowKind.WEEKLY],
                mapped[QuotaWindowKind.MONTHLY],
            )
            return QuotaSnapshot(
                platformId = PlatformIds.KIMI_CODE,
                platformName = PlatformRegistry.displayName(PlatformIds.KIMI_CODE),
                windows = windows,
                primaryDisplay = formatUsagePrimaryDisplay(windows),
                updatedAtEpochMs = updatedAtEpochMs,
                accountLabel = formatMembershipLevel(dto.user?.membership?.level),
            )
        }

        internal fun KimiCodeWindowDto.toQuotaWindow(nowEpochMs: Long): QuotaWindow? {
            val limitValue = limit?.toDoubleOrNull()
            var usedValue = used?.toDoubleOrNull()
            if (usedValue == null && limitValue != null) {
                remaining?.toDoubleOrNull()?.let { usedValue = limitValue - it }
            }
            val percent = if (limitValue != null && limitValue > 0.0 && usedValue != null) {
                usedValue / limitValue * 100.0
            } else {
                null
            }
            val resetInSec = parseIso8601EpochMs(resetTime)
                ?.let { ((it - nowEpochMs) / 1000L).coerceAtLeast(0L) }
            if (percent == null && resetInSec == null) return null
            return QuotaWindow(
                // Placeholder kind; caller re-keys via classifyWindow / weekly slot.
                kind = QuotaWindowKind.WEEKLY,
                usedPercent = percent,
                resetInSec = resetInSec,
            )
        }

        internal fun KimiCodeWindowKindDto.durationSeconds(): Long? {
            val value = duration ?: return null
            if (value <= 0L) return null
            val unit = timeUnit.orEmpty().uppercase()
            val multiplier = when {
                "SECOND" in unit -> 1L
                "MINUTE" in unit -> 60L
                "HOUR" in unit -> 3600L
                "DAY" in unit -> 86_400L
                "MONTH" in unit -> 30L * 86_400L
                else -> return null
            }
            return value * multiplier
        }

        /** `LEVEL_INTERMEDIATE` → `Intermediate`; blank → "". */
        fun formatMembershipLevel(level: String?): String {
            val stripped = level.orEmpty().trim().removePrefix("LEVEL_").trim()
            if (stripped.isBlank()) return ""
            return stripped.lowercase().replaceFirstChar { it.uppercase() }
        }

        /**
         * Minimal ISO-8601 parser: `yyyy-MM-ddTHH:mm:ss[.frac][Z|±HH:MM]`.
         * Returns epoch milliseconds, or null when the shape is not recognised.
         */
        fun parseIso8601EpochMs(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val parts = raw.trim().split('T', 't')
                if (parts.size != 2) return null
                val dateParts = parts[0].split('-')
                if (dateParts.size != 3) return null
                val year = dateParts[0].toInt()
                val month = dateParts[1].toInt()
                val day = dateParts[2].toInt()
                if (month !in 1..12 || day !in 1..31) return null

                var timePart = parts[1]
                var offsetSeconds = 0L
                val zoneIndex = timePart.indexOfAny(charArrayOf('Z', 'z', '+', '-'))
                if (zoneIndex >= 0) {
                    val zone = timePart.substring(zoneIndex)
                    timePart = timePart.substring(0, zoneIndex)
                    if (!zone.equals("Z", ignoreCase = true)) {
                        val sign = if (zone.startsWith("-")) -1L else 1L
                        val digits = zone.substring(1).replace(":", "")
                        if (digits.length != 4) return null
                        offsetSeconds = sign * (
                            digits.substring(0, 2).toLong() * 3600L +
                                digits.substring(2, 4).toLong() * 60L
                            )
                    }
                }
                val dotIndex = timePart.indexOf('.')
                if (dotIndex >= 0) {
                    timePart = timePart.substring(0, dotIndex)
                }
                val timeParts = timePart.split(':')
                if (timeParts.size != 3) return null
                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()
                val second = timeParts[2].toInt()
                if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null

                val epochSeconds = daysFromCivil(year, month, day) * 86_400L +
                    hour * 3600L + minute * 60L + second - offsetSeconds
                epochSeconds * 1000L
            }.getOrNull()
        }

        /** Howard Hinnant's days-from-civil; days since 1970-01-01. */
        private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
            val y = if (month <= 2) year - 1 else year
            val era = Math.floorDiv(y.toLong(), 400L)
            val yoe = y - era * 400L
            val mp = (month + 9) % 12
            val doy = (153L * mp + 2L) / 5L + day - 1L
            val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
            return era * 146_097L + doe - 719_468L
        }
    }
}

@Serializable
data class KimiCodeUsageResponse(
    val user: KimiCodeUserDto? = null,
    val usage: KimiCodeWindowDto? = null,
    val limits: List<KimiCodeLimitDto> = emptyList(),
)

@Serializable
data class KimiCodeUserDto(
    val membership: KimiCodeMembershipDto? = null,
)

@Serializable
data class KimiCodeMembershipDto(
    val level: String? = null,
)

@Serializable
data class KimiCodeLimitDto(
    val window: KimiCodeWindowKindDto? = null,
    val detail: KimiCodeWindowDto? = null,
)

@Serializable
data class KimiCodeWindowKindDto(
    val duration: Long? = null,
    val timeUnit: String? = null,
)

@Serializable
data class KimiCodeWindowDto(
    val limit: String? = null,
    val used: String? = null,
    val remaining: String? = null,
    val resetTime: String? = null,
)
