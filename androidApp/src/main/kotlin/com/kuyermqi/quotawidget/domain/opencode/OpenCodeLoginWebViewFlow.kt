package com.kuyermqi.quotawidget.domain.opencode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.opencode.OpenCodeGoClient
import com.kuyermqi.quotawidget.webview.InAppWebViewActivity
import com.kuyermqi.quotawidget.webview.InAppWebViewFlow
import com.kuyermqi.quotawidget.webview.InAppWebViewHost

/**
 * OpenCode console OAuth / cookie capture for Go quota.
 * Isolated from [InAppWebViewActivity] so other platforms can plug in their own flows.
 */
class OpenCodeLoginWebViewFlow(
    private val host: InAppWebViewHost,
) : InAppWebViewFlow {
    private var loginCompleted = false
    private var goProbeStarted = false
    private var cookieCaptureAttempts = 0

    override fun onPrepare() {
        // Drop anonymous pre-auth iron-session cookies so we capture the post-OAuth value.
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    override fun createWebViewClient(): WebViewClient =
        object : WebViewClient() {
            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                super.onPageFinished(view, finishedUrl)
                handleNavigation(view, finishedUrl.orEmpty())
            }
        }

    override fun onDestroy() {
        // Host cancels Handler callbacks; nothing else to clear.
    }

    private fun handleNavigation(view: WebView?, finishedUrl: String) {
        if (loginCompleted || view == null) return
        val workspaceId = OpenCodeGoClient.extractWorkspaceId(finishedUrl) ?: return
        if (!finishedUrl.contains("/go")) {
            if (!goProbeStarted) {
                goProbeStarted = true
                Log.i(TAG, "login reached workspace; loading /go")
                view.loadUrl("${OpenCodeGoClient.BASE_URL}/workspace/$workspaceId/go")
            }
            return
        }
        host.postDelayed(COOKIE_SETTLE_DELAY_MS) {
            attemptCookieCapture(workspaceId)
        }
    }

    private fun attemptCookieCapture(workspaceId: String) {
        if (host.isFinishingOrDestroyed || loginCompleted) return
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        val cookieHeader = sequenceOf(
            cookieManager.getCookie("${OpenCodeGoClient.BASE_URL}/workspace/$workspaceId/go"),
            cookieManager.getCookie("${OpenCodeGoClient.BASE_URL}/workspace/$workspaceId"),
            cookieManager.getCookie(OpenCodeGoClient.BASE_URL),
            cookieManager.getCookie("${OpenCodeGoClient.BASE_URL}/"),
        ).firstOrNull { header ->
            OpenCodeGoClient.extractAuthCookie(header) != null
        }
        if (cookieHeader.isNullOrBlank()) {
            retryCookieCapture(workspaceId, "missing auth cookie")
            return
        }
        loginCompleted = true
        val authLen = OpenCodeGoClient.extractAuthCookie(cookieHeader)?.length ?: 0
        Log.i(
            TAG,
            "login complete workspace=$workspaceId authLen=$authLen headerLen=${cookieHeader.length}",
        )
        host.finishWithResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_RESULT_WORKSPACE_ID, workspaceId)
                .putExtra(EXTRA_RESULT_AUTH_COOKIE, cookieHeader),
        )
    }

    private fun retryCookieCapture(workspaceId: String, reason: String) {
        if (loginCompleted || host.isFinishingOrDestroyed) return
        cookieCaptureAttempts += 1
        Log.w(TAG, "login cookie retry=$cookieCaptureAttempts reason=$reason")
        if (cookieCaptureAttempts >= MAX_COOKIE_ATTEMPTS) {
            Log.e(TAG, "login cookie capture failed; finishing without result")
            host.finishCanceled()
            return
        }
        host.postDelayed(COOKIE_RETRY_DELAY_MS) {
            attemptCookieCapture(workspaceId)
        }
    }

    companion object {
        const val FLOW_ID = "opencode_login"

        const val EXTRA_RESULT_WORKSPACE_ID = "extra_result_workspace_id"
        const val EXTRA_RESULT_AUTH_COOKIE = "extra_result_auth_cookie"

        private const val TAG = "OpenCodeLogin"
        private const val COOKIE_SETTLE_DELAY_MS = 500L
        private const val COOKIE_RETRY_DELAY_MS = 600L
        private const val MAX_COOKIE_ATTEMPTS = 8

        fun createIntent(context: Context): Intent =
            InAppWebViewActivity.createIntent(
                context = context,
                url = OpenCodeGoClient.LOGIN_URL,
                title = context.getString(R.string.opencode_login_title),
                flowId = FLOW_ID,
            )
    }
}
