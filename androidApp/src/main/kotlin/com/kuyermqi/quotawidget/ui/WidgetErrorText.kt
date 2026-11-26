package com.kuyermqi.quotawidget.ui

import android.content.Context
import android.content.res.Resources
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.WidgetErrorKind

/**
 * The only place that turns a stored [WidgetErrorKind] into user-visible text, so a widget,
 * the home list and the settings page always agree on the wording and language.
 */
fun widgetErrorText(kind: WidgetErrorKind, resources: Resources): String = when (kind) {
    WidgetErrorKind.SessionExpired ->
        resources.getString(R.string.widget_error_session_expired)
    WidgetErrorKind.QuotaUnavailable ->
        resources.getString(R.string.widget_error_quota_unavailable)
    is WidgetErrorKind.HttpStatus ->
        resources.getString(R.string.widget_error_http_status, kind.code)
    WidgetErrorKind.Network ->
        resources.getString(R.string.widget_error_network)
    WidgetErrorKind.InvalidBaseUrl ->
        resources.getString(R.string.widget_error_invalid_base_url)
    WidgetErrorKind.ParseFailure ->
        resources.getString(R.string.widget_error_parse_failure)
    is WidgetErrorKind.Unknown -> resources.getString(R.string.widget_fetch_failed)
}

fun widgetErrorText(kind: WidgetErrorKind, context: Context): String =
    widgetErrorText(kind, context.resources)
