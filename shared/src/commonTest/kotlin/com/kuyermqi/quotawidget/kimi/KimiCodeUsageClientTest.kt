package com.kuyermqi.quotawidget.kimi

import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KimiCodeUsageClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val livePayload = """
        {
          "user": {
            "userId": "d9v6dovaa0v22fc7pr0g",
            "region": "REGION_CN",
            "membership": {"level": "LEVEL_INTERMEDIATE"},
            "businessId": ""
          },
          "usage": {
            "limit": "100",
            "used": "32",
            "remaining": "68",
            "resetTime": "2026-08-21T01:07:45.776567Z"
          },
          "limits": [
            {
              "window": {"duration": 300, "timeUnit": "TIME_UNIT_MINUTE"},
              "detail": {
                "limit": "100",
                "used": "20",
                "remaining": "80",
                "resetTime": "2026-08-17T14:07:45.776567Z"
              }
            }
          ],
          "parallel": {"limit": 20},
          "totalQuota": {},
          "authentication": {"method": "METHOD_API_KEY", "scope": "FEATURE_CODING"},
          "subType": "TYPE_PURCHASE",
          "domain": "DOMAIN_NEXUS"
        }
    """.trimIndent()

    @Test
    fun toSnapshot_mapsLivePayloadToFiveHourAndWeeklyWindows() {
        val dto = json.decodeFromString(KimiCodeUsageResponse.serializer(), livePayload)
        val snapshot = KimiCodeUsageClient.toSnapshot(
            dto,
            updatedAtEpochMs = NOW_EPOCH_MS,
            nowEpochMs = NOW_EPOCH_MS,
        )
        assertEquals(2, snapshot.windows.size)

        val fiveHour = snapshot.windows[0]
        assertEquals(QuotaWindowKind.FIVE_HOUR, fiveHour.kind)
        assertEquals(20.0, fiveHour.usedPercent)
        assertEquals(7_665L, fiveHour.resetInSec)

        val weekly = snapshot.windows[1]
        assertEquals(QuotaWindowKind.WEEKLY, weekly.kind)
        assertEquals(32.0, weekly.usedPercent)
        assertEquals(306_465L, weekly.resetInSec)

        assertEquals("Intermediate", snapshot.accountLabel)
        assertEquals("kimi_code", snapshot.platformId)
        assertEquals("Kimi Code", snapshot.platformName)
        assertTrue(snapshot.primaryDisplay.contains("5h"))
        assertTrue(snapshot.primaryDisplay.contains("周"))
    }

    @Test
    fun toSnapshot_toleratesUnquotedNumbers() {
        val payload = livePayload
            .replace("\"limit\": \"100\"", "\"limit\": 100")
            .replace("\"used\": \"32\"", "\"used\": 32")
        val dto = json.decodeFromString(KimiCodeUsageResponse.serializer(), payload)
        val snapshot = KimiCodeUsageClient.toSnapshot(
            dto,
            updatedAtEpochMs = NOW_EPOCH_MS,
            nowEpochMs = NOW_EPOCH_MS,
        )
        val weekly = snapshot.windows.first { it.kind == QuotaWindowKind.WEEKLY }
        assertEquals(32.0, weekly.usedPercent)
    }

    @Test
    fun toSnapshot_derivesUsedFromRemaining() {
        val payload = """
            {"usage": {"limit": "50", "remaining": "40"}, "limits": []}
        """.trimIndent()
        val dto = json.decodeFromString(KimiCodeUsageResponse.serializer(), payload)
        val snapshot = KimiCodeUsageClient.toSnapshot(
            dto,
            updatedAtEpochMs = NOW_EPOCH_MS,
            nowEpochMs = NOW_EPOCH_MS,
        )
        val weekly = snapshot.windows.single()
        assertEquals(QuotaWindowKind.WEEKLY, weekly.kind)
        assertEquals(20.0, weekly.usedPercent)
        assertNull(weekly.resetInSec)
        assertEquals("", snapshot.accountLabel)
    }

    @Test
    fun toSnapshot_emptyPayloadYieldsNoWindows() {
        val dto = json.decodeFromString(KimiCodeUsageResponse.serializer(), "{}")
        val snapshot = KimiCodeUsageClient.toSnapshot(
            dto,
            updatedAtEpochMs = NOW_EPOCH_MS,
            nowEpochMs = NOW_EPOCH_MS,
        )
        assertTrue(snapshot.windows.isEmpty())
        assertEquals("", snapshot.primaryDisplay)
    }

    @Test
    fun parseIso8601EpochMs_handlesUtcFractionAndOffsets() {
        assertEquals(0L, KimiCodeUsageClient.parseIso8601EpochMs("1970-01-01T00:00:00Z"))
        assertEquals(
            1_787_274_465_000L,
            KimiCodeUsageClient.parseIso8601EpochMs("2026-08-21T01:07:45.776567Z"),
        )
        assertEquals(
            1_787_274_465_000L,
            KimiCodeUsageClient.parseIso8601EpochMs("2026-08-21T09:07:45+08:00"),
        )
        assertEquals(
            1_787_274_465_000L,
            KimiCodeUsageClient.parseIso8601EpochMs("2026-08-20T20:07:45-05:00"),
        )
    }

    @Test
    fun parseIso8601EpochMs_rejectsMalformedInput() {
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs(null))
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs(""))
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs("2026-08-21"))
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs("not a date"))
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs("2026-13-99T99:99:99Z"))
    }

    @Test
    fun formatMembershipLevel_prettyPrintsKnownLevels() {
        assertEquals("Intermediate", KimiCodeUsageClient.formatMembershipLevel("LEVEL_INTERMEDIATE"))
        assertEquals("Basic", KimiCodeUsageClient.formatMembershipLevel("LEVEL_BASIC"))
        assertEquals("", KimiCodeUsageClient.formatMembershipLevel(null))
        assertEquals("", KimiCodeUsageClient.formatMembershipLevel(""))
    }

    private companion object {
        /** 2026-08-17T12:00:00Z */
        const val NOW_EPOCH_MS = 1_786_968_000_000L
    }
}
