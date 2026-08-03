package com.kuyermqi.quotawidget.webview

import android.content.Intent
import android.webkit.WebViewClient

/**
 * Optional navigation / result policy for [InAppWebViewActivity].
 * Keep platform-specific login capture in feature packages so the Activity stays a reusable shell.
 */
interface InAppWebViewFlow {
    /** Called once before the WebView loads (e.g. clear cookies). */
    fun onPrepare() {}

    fun createWebViewClient(): WebViewClient

    fun onDestroy() {}
}

/** Callbacks the Activity exposes to a [InAppWebViewFlow]. */
interface InAppWebViewHost {
    val isFinishingOrDestroyed: Boolean

    fun postDelayed(delayMs: Long, action: () -> Unit)

    fun cancelPendingPosts()

    fun finishWithResult(resultCode: Int, data: Intent? = null)

    fun finishCanceled()
}

/** No-op client for plain browsing (docs / tips links). */
class PassthroughWebViewClient : WebViewClient()

fun InAppWebViewFlow?.orPassthrough(): InAppWebViewFlow =
    this ?: object : InAppWebViewFlow {
        override fun createWebViewClient(): WebViewClient = PassthroughWebViewClient()
    }
