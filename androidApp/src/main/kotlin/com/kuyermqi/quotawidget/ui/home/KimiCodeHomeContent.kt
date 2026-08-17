package com.kuyermqi.quotawidget.ui.home

import android.content.res.Resources
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.KimiCodeSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.KimiCodeConfigContent
import com.kuyermqi.quotawidget.ui.usage.formatUsageWindowSummary
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

class KimiCodeHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
) {
    var saved by mutableStateOf(KimiCodeSettings())
        internal set
    var draftApiKey by mutableStateOf("")
        internal set
    var draftWindowKind by mutableStateOf(UsageWindowKind.WEEKLY)
        internal set
    var draftUsageDisplayMode by mutableStateOf(UsageDisplayMode.USED)
        internal set
    var draftUsageProgressStyle by mutableStateOf(UsageProgressStyle.BAR)
        internal set
    var lastWindows by mutableStateOf<List<QuotaWindow>>(emptyList())
        internal set
    var lastAccountLabel by mutableStateOf("")
        internal set
    var isSaving by mutableStateOf(false)
        internal set
    var saveError by mutableStateOf<String?>(null)
        internal set
    var loaded by mutableStateOf(false)
        internal set

    val isConfigured: Boolean
        get() = saved.isConfigured

    val isDirty: Boolean
        get() = loaded && (
            draftApiKey != saved.apiKey ||
                draftWindowKind != saved.widgetWindowKind ||
                draftUsageDisplayMode != saved.usageDisplayMode ||
                draftUsageProgressStyle != saved.usageProgressStyle
            )

    fun applyLoaded(settings: KimiCodeSettings) {
        saved = settings
        draftApiKey = settings.apiKey
        draftWindowKind = settings.widgetWindowKind
        draftUsageDisplayMode = settings.usageDisplayMode
        draftUsageProgressStyle = settings.usageProgressStyle
        loaded = true
    }

    fun summaryLabel(
        resources: Resources,
        widgetState: WidgetDisplayState,
        loadingMsg: String,
        reauthMsg: String,
    ): String? {
        if (!isConfigured) return null
        val unavailable = resources.getString(R.string.usage_unavailable)
        fun format(windows: List<QuotaWindow>): String? =
            windows.takeIf { it.isNotEmpty() }?.let {
                formatUsageWindowSummary(
                    resources = resources,
                    windows = it,
                    windowKind = saved.widgetWindowKind,
                    usageDisplayMode = saved.usageDisplayMode,
                    fallback = unavailable,
                )
            }
        return when (widgetState) {
            is WidgetDisplayState.Success -> format(widgetState.snapshot.windows)
            WidgetDisplayState.Loading -> format(lastWindows) ?: loadingMsg
            is WidgetDisplayState.Error -> format(lastWindows)
            WidgetDisplayState.NeedsReauth -> reauthMsg
            WidgetDisplayState.NotConfigured -> null
        }
    }

    internal fun repository(): PlatformSettingsRepository = settingsRepository
}

@Composable
fun rememberKimiCodeHomeState(
    settingsRepository: PlatformSettingsRepository,
): KimiCodeHomeState = remember(settingsRepository) {
    KimiCodeHomeState(settingsRepository)
}

@Composable
fun KimiCodeHomeEffects(state: KimiCodeHomeState): WidgetDisplayState {
    val observed by state.repository().observeWidgetState(PlatformIds.KIMI_CODE)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)
    LaunchedEffect(observed) {
        val success = observed as? WidgetDisplayState.Success ?: return@LaunchedEffect
        state.lastWindows = success.snapshot.windows
        state.lastAccountLabel = success.snapshot.accountLabel
    }
    return observed
}

@Composable
fun ColumnScope.KimiCodeHomeContent(
    state: KimiCodeHomeState,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    KimiCodeConfigContent(
        apiKey = state.draftApiKey,
        onApiKeyChange = {
            state.draftApiKey = it
            state.saveError = null
        },
        accountLabel = state.lastAccountLabel,
        windows = state.lastWindows,
        widgetWindowKind = state.draftWindowKind,
        onWidgetWindowKindChange = {
            state.draftWindowKind = it
            state.saveError = null
        },
        usageDisplayMode = state.draftUsageDisplayMode,
        onUsageDisplayModeChange = {
            state.draftUsageDisplayMode = it
            state.saveError = null
        },
        usageProgressStyle = state.draftUsageProgressStyle,
        onUsageProgressStyleChange = {
            state.draftUsageProgressStyle = it
            state.saveError = null
        },
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        saveError = state.saveError,
        onSave = {
            scope.launch {
                val trimmedKey = state.draftApiKey.trim()
                if (trimmedKey.isNotEmpty() && !trimmedKey.startsWith(KIMI_CODE_API_KEY_PREFIX)) {
                    state.saveError = context.getString(R.string.kimi_code_api_key_invalid)
                    return@launch
                }
                // Base on latest persisted settings so concurrent updates are kept.
                val next = state.repository().getKimiCodeSettings().copy(
                    apiKey = trimmedKey,
                    widgetWindowKind = state.draftWindowKind,
                    usageDisplayMode = state.draftUsageDisplayMode,
                    usageProgressStyle = state.draftUsageProgressStyle,
                )
                state.isSaving = true
                state.saveError = null
                try {
                    state.repository().saveKimiCodeSettings(next)
                    state.saved = next
                    state.draftApiKey = next.apiKey
                    if (!next.isConfigured) {
                        state.lastWindows = emptyList()
                        state.lastAccountLabel = ""
                    }
                    state.saveError = when (
                        val result = onRefreshPlatform(PlatformIds.KIMI_CODE)
                    ) {
                        is WidgetDisplayState.Error -> result.message
                        else -> null
                    }
                    WidgetGlanceState.syncAndUpdate(context, "kimi_code_save")
                } finally {
                    state.isSaving = false
                }
            }
        },
    )
}

private const val KIMI_CODE_API_KEY_PREFIX = "sk-kimi-"
