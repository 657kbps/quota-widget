package com.kuyermqi.quotawidget.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kuyermqi.quotawidget.platform.PlatformIds

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quota_widget_settings")

internal val REFRESH_PHASE_PLATFORM_IDS: List<String> = buildList {
    add(PlatformIds.DEEPSEEK)
    add(PlatformIds.OPENCODE_GO)
    add(PlatformIds.CODEX)
    add(PlatformIds.NEW_API)
    add(PlatformIds.MAGPIE)
    add(PlatformIds.KIMI_CODE)
    add(PlatformIds.ZHIPU)
    addAll(PlatformIds.sessionPlatforms)
}

internal object SettingsKeys {
    val API_KEY_ENCRYPTED = stringPreferencesKey("deepseek_api_key_enc")
    val CURRENCY = stringPreferencesKey("deepseek_currency")
    val OPENCODE_WORKSPACE_ID = stringPreferencesKey("opencode_workspace_id")
    val OPENCODE_WORKSPACE_NAME = stringPreferencesKey("opencode_workspace_name")
    val OPENCODE_AUTH_COOKIE_ENC = stringPreferencesKey("opencode_auth_cookie_enc")
    val OPENCODE_API_KEY_ENC = stringPreferencesKey("opencode_api_key_enc")
    val OPENCODE_AUTH_METHOD = stringPreferencesKey("opencode_auth_method")
    val OPENCODE_WIDGET_WINDOW = stringPreferencesKey("opencode_go_widget_window")
    val OPENCODE_USAGE_DISPLAY = stringPreferencesKey("opencode_go_usage_display")
    val OPENCODE_USAGE_PROGRESS_STYLE =
        stringPreferencesKey("opencode_go_usage_progress_style")
    val OPENCODE_USAGE_RESET_STYLE =
        stringPreferencesKey("opencode_go_usage_reset_style")
    val CODEX_ACCESS_ENC = stringPreferencesKey("codex_access_token_enc")
    val CODEX_REFRESH_ENC = stringPreferencesKey("codex_refresh_token_enc")
    val CODEX_ID_ENC = stringPreferencesKey("codex_id_token_enc")
    val CODEX_ACCOUNT_ID = stringPreferencesKey("codex_account_id")
    val CODEX_EXPIRES_AT = longPreferencesKey("codex_expires_at")
    val CODEX_EMAIL = stringPreferencesKey("codex_email")
    val CODEX_PLAN_TYPE = stringPreferencesKey("codex_plan_type")
    val CODEX_WIDGET_WINDOW = stringPreferencesKey("codex_widget_window")
    val CODEX_USAGE_DISPLAY = stringPreferencesKey("codex_usage_display")
    val CODEX_USAGE_PROGRESS_STYLE = stringPreferencesKey("codex_usage_progress_style")
    val CODEX_USAGE_RESET_STYLE = stringPreferencesKey("codex_usage_reset_style")
    val NEW_API_BASE_URL = stringPreferencesKey("new_api_base_url")
    val NEW_API_KEY_ENC = stringPreferencesKey("new_api_key_enc")
    val NEW_API_QUOTA_PER_USD = longPreferencesKey("new_api_quota_per_usd")
    val NEW_API_USAGE_DISPLAY = stringPreferencesKey("new_api_usage_display")
    val NEW_API_USAGE_PROGRESS_STYLE = stringPreferencesKey("new_api_usage_progress_style")
    val MAGPIE_BASE_URL = stringPreferencesKey("magpie_base_url")
    val MAGPIE_KEY_ENC = stringPreferencesKey("magpie_key_enc")
    val MAGPIE_PROVIDERS = stringPreferencesKey("magpie_providers")
    val MAGPIE_SELECTED_PROVIDER = stringPreferencesKey("magpie_selected_provider")
    val MAGPIE_WIDGET_WINDOW = stringPreferencesKey("magpie_widget_window")
    val MAGPIE_USAGE_DISPLAY = stringPreferencesKey("magpie_usage_display")
    val MAGPIE_USAGE_PROGRESS_STYLE = stringPreferencesKey("magpie_usage_progress_style")
    val MAGPIE_USAGE_RESET_STYLE = stringPreferencesKey("magpie_usage_reset_style")
    val KIMI_CODE_API_KEY_ENC = stringPreferencesKey("kimi_code_api_key_enc")
    val KIMI_CODE_REGION = stringPreferencesKey("kimi_code_region")
    val KIMI_CODE_WIDGET_WINDOW = stringPreferencesKey("kimi_code_widget_window")
    val KIMI_CODE_USAGE_DISPLAY = stringPreferencesKey("kimi_code_usage_display")
    val KIMI_CODE_USAGE_PROGRESS_STYLE =
        stringPreferencesKey("kimi_code_usage_progress_style")
    val KIMI_CODE_USAGE_RESET_STYLE =
        stringPreferencesKey("kimi_code_usage_reset_style")
    val ZHIPU_API_KEY_ENC = stringPreferencesKey("zhipu_api_key_enc")
    val ZHIPU_REGION = stringPreferencesKey("zhipu_region")
    val ZHIPU_WIDGET_WINDOW = stringPreferencesKey("zhipu_widget_window")
    val ZHIPU_USAGE_DISPLAY = stringPreferencesKey("zhipu_usage_display")
    val ZHIPU_USAGE_PROGRESS_STYLE = stringPreferencesKey("zhipu_usage_progress_style")
    val ZHIPU_USAGE_RESET_STYLE = stringPreferencesKey("zhipu_usage_reset_style")
    val ACTIVE_PLATFORM_ID = stringPreferencesKey("active_platform_id")
    val LEGACY_WIDGET_STATUS = stringPreferencesKey("widget_status")
    val LEGACY_WIDGET_ERROR = stringPreferencesKey("widget_error")
    val LEGACY_WIDGET_PLATFORM_ID = stringPreferencesKey("widget_platform_id")
    val LEGACY_WIDGET_PLATFORM_NAME = stringPreferencesKey("widget_platform_name")
    val LEGACY_WIDGET_CURRENCY = stringPreferencesKey("widget_currency")
    val LEGACY_WIDGET_TOTAL = stringPreferencesKey("widget_total")
    val LEGACY_WIDGET_FORMATTED = stringPreferencesKey("widget_formatted")
    val LEGACY_WIDGET_WINDOWS = stringPreferencesKey("widget_windows")
    val LEGACY_WIDGET_UPDATED_AT = longPreferencesKey("widget_updated_at")
    val LEGACY_REFRESH_ICON_PHASE = stringPreferencesKey("refresh_icon_phase")
    val LEGACY_REFRESH_STARTED_AT = longPreferencesKey("refresh_started_at")
    val PLATFORM_TIP_DISMISSED = booleanPreferencesKey("platform_tip_dismissed")
    val OEM_BACKGROUND_TIP_DISMISSED = booleanPreferencesKey("oem_background_tip_dismissed")
    val DARK_THEME_MODE = stringPreferencesKey("dark_theme_mode")
    val THEME_COLOR_MODE = stringPreferencesKey("theme_color_mode")
    val CUSTOM_SEED_COLOR_ARGB = intPreferencesKey("custom_seed_color_argb")
    val REFRESH_INTERVAL_MINUTES = intPreferencesKey("refresh_interval_minutes")
    val SHOW_WIDGET_UPDATED_AT = booleanPreferencesKey("show_widget_updated_at")
    val CHECK_FOR_UPDATES_ON_LAUNCH = booleanPreferencesKey("check_for_updates_on_launch")
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val UPDATE_IGNORED_VERSION = stringPreferencesKey("update_ignored_version")
}

internal val API_KEY_AD = "deepseek_api_key".encodeToByteArray()
internal val OPENCODE_AUTH_AD = "opencode_auth_cookie".encodeToByteArray()
internal val OPENCODE_API_KEY_AD = "opencode_api_key".encodeToByteArray()
internal val CODEX_ACCESS_AD = "codex_access_token".encodeToByteArray()
internal val CODEX_REFRESH_AD = "codex_refresh_token".encodeToByteArray()
internal val CODEX_ID_AD = "codex_id_token".encodeToByteArray()
internal val NEW_API_KEY_AD = "new_api_key".encodeToByteArray()
internal val MAGPIE_KEY_AD = "magpie_key".encodeToByteArray()
internal val KIMI_CODE_KEY_AD = "kimi_code_api_key".encodeToByteArray()
internal val ZHIPU_KEY_AD = "zhipu_api_key".encodeToByteArray()

internal fun sessionAccessKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_access_enc")

internal fun sessionRefreshKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_refresh_enc")

internal fun sessionApiKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_api_key_enc")

internal fun sessionAuthMethodKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_auth_method")

internal fun sessionExpiresAtKey(platformId: String) =
    longPreferencesKey("session_${platformId}_expires_at")

internal fun sessionAccountIdKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_account_id")

internal fun sessionEmailKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_email")

internal fun sessionPlanKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_plan")

internal fun sessionProjectIdKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_project_id")

internal fun sessionDomainKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_domain")

internal fun sessionUidKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_uid")

internal fun sessionEnterpriseIdKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_enterprise_id")

internal fun sessionWidgetWindowKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_widget_window")

internal fun sessionUsageDisplayKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_usage_display")

internal fun sessionUsageProgressStyleKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_usage_progress_style")

internal fun sessionUsageResetStyleKey(platformId: String) =
    stringPreferencesKey("session_${platformId}_usage_reset_style")

internal fun widgetStatusKey(platformId: String) =
    stringPreferencesKey("widget_${platformId}_status")

internal fun widgetErrorKey(platformId: String) =
    stringPreferencesKey("widget_${platformId}_error")

internal fun widgetPlatformIdKey(platformId: String) =
    stringPreferencesKey("widget_${platformId}_platform_id")

internal fun widgetPlatformNameKey(platformId: String) =
    stringPreferencesKey("widget_${platformId}_platform_name")

internal fun widgetCurrencyKey(platformId: String) =
    stringPreferencesKey("widget_${platformId}_currency")

internal fun widgetTotalKey(platformId: String) =
    stringPreferencesKey("widget_${platformId}_total")

internal fun widgetFormattedKey(platformId: String) =
    stringPreferencesKey("widget_${platformId}_formatted")

internal fun widgetWindowsKey(platformId: String) =
    stringPreferencesKey("widget_${platformId}_windows")

internal fun widgetUpdatedAtKey(platformId: String) =
    longPreferencesKey("widget_${platformId}_updated_at")

internal fun widgetUnlimitedKey(platformId: String) =
    booleanPreferencesKey("widget_${platformId}_unlimited")

internal fun widgetUsedDisplayKey(platformId: String) =
    stringPreferencesKey("widget_${platformId}_used_display")

internal fun widgetEmptyLimitedQuotaKey(platformId: String) =
    booleanPreferencesKey("widget_${platformId}_empty_limited_quota")

internal fun widgetTokenExpiredKey(platformId: String) =
    booleanPreferencesKey("widget_${platformId}_token_expired")

internal fun widgetQuotaOverspentKey(platformId: String) =
    booleanPreferencesKey("widget_${platformId}_quota_overspent")

internal fun refreshPhaseKey(platformId: String) =
    stringPreferencesKey("refresh_${platformId}_icon_phase")

internal fun refreshStartedAtKey(platformId: String) =
    longPreferencesKey("refresh_${platformId}_started_at")

