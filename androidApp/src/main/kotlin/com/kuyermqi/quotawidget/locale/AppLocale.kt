package com.kuyermqi.quotawidget.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.kuyermqi.quotawidget.domain.AppLanguage
import java.util.Locale

/**
 * Applies [AppLanguage] to activity and widget contexts on every supported API level.
 *
 * System per-app locales only exist on API 33+, so the selected language is applied by
 * wrapping each [Context] with an overridden [Configuration] instead. Unsupported system
 * languages fall back to English.
 */
object AppLocale {
    private const val FALLBACK_LANGUAGE_TAG = "en"

    /** Languages the app ships resources for. */
    private val supportedLanguages = setOf("en", "zh", "ja", "de", "fr", "ru")

    private val traditionalChineseRegions = setOf("TW", "HK", "MO")

    @Volatile
    private var language: AppLanguage = AppLanguage.FollowSystem

    private val activities = mutableSetOf<LocaleAwareActivity>()

    fun current(): AppLanguage = language

    /** Locale used for date formatting; falls back to English for unsupported system languages. */
    fun localeOf(context: Context): Locale {
        val tag = language.languageTag
        if (tag.isNotBlank()) return Locale.forLanguageTag(tag)
        return resolveSystemLocale(context.resources.configuration.locales[0])
    }

    fun wrap(base: Context): Context {
        val tag = language.languageTag
        if (tag.isNotBlank()) return withLocale(base, tag)
        val systemLocale = base.resources.configuration.locales[0]
        return when (systemLocale.language) {
            !in supportedLanguages -> withLocale(base, FALLBACK_LANGUAGE_TAG)
            "zh" -> withLocale(base, resolveSystemLocale(systemLocale).toLanguageTag())
            else -> base
        }
    }

    /** Returns true when the stored language actually changed. */
    fun update(value: AppLanguage): Boolean {
        if (language == value) return false
        language = value
        return true
    }

    fun register(activity: LocaleAwareActivity) {
        activities.add(activity)
    }

    fun unregister(activity: LocaleAwareActivity) {
        activities.remove(activity)
    }

    fun recreateActivities() {
        activities.toList().forEach { it.recreate() }
    }

    private fun resolveSystemLocale(systemLocale: Locale): Locale {
        if (systemLocale.language !in supportedLanguages) return Locale.forLanguageTag(FALLBACK_LANGUAGE_TAG)
        if (systemLocale.language != "zh") return systemLocale
        val traditional = systemLocale.script == "Hant" ||
            systemLocale.country in traditionalChineseRegions
        return Locale.forLanguageTag(if (traditional) "zh-Hant" else "zh-Hans")
    }

    private fun withLocale(base: Context, languageTag: String): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(languageTag))
        return base.createConfigurationContext(configuration)
    }
}
