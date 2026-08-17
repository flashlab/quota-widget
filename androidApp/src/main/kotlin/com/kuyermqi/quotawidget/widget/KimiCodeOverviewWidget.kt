package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toKimiCodeUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toKimiCodeUsageProgressStyle
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewSizeComfortable
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewSizeCompact
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewWidgetContent

/** Kimi Code overview: 5H + weekly rows (the two windows the usages API exposes). */
class KimiCodeOverviewWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SizeCompact, SizeComfortable),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.KIMI_CODE) { state, refreshPhase, openApp, platformTitle ->
            val prefs = currentState<Preferences>()
            UsageOverviewWidgetContent(
                platformId = PlatformIds.KIMI_CODE,
                platformTitle = platformTitle,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                usageDisplayMode = prefs.toKimiCodeUsageDisplayMode(),
                usageProgressStyle = prefs.toKimiCodeUsageProgressStyle(),
                overviewKinds = OVERVIEW_KINDS,
                showResetLabels = true,
            )
        }
    }

    companion object {
        val SizeCompact: DpSize = UsageOverviewSizeCompact
        val SizeComfortable: DpSize = UsageOverviewSizeComfortable
        private val OVERVIEW_KINDS = listOf(QuotaWindowKind.FIVE_HOUR, QuotaWindowKind.WEEKLY)
    }
}

class KimiCodeOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KimiCodeOverviewWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
