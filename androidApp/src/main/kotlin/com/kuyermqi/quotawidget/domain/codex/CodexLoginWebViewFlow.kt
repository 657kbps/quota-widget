package com.kuyermqi.quotawidget.domain.codex

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.codex.CodexOAuth
import com.kuyermqi.quotawidget.webview.InAppWebViewActivity
import com.kuyermqi.quotawidget.webview.InAppWebViewFlow
import com.kuyermqi.quotawidget.webview.InAppWebViewHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Codex CLI OAuth PKCE in an in-app WebView.
 * Intercepts [CodexOAuth.REDIRECT_URI] without loading cleartext localhost.
 */
class CodexLoginWebViewFlow(
    private val host: InAppWebViewHost,
    private val expectedState: String,
    private val codeVerifier: String,
) : InAppWebViewFlow {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val oauth = CodexOAuth()
    private var loginCompleted = false
    private var exchangeStarted = false

    override fun onPrepare() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    override fun createWebViewClient(): WebViewClient =
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                return handlePossibleCallback(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                handlePossibleCallback(url.orEmpty())
        }

    override fun onDestroy() {
        scope.cancel()
        oauth.close()
    }

    private fun handlePossibleCallback(url: String): Boolean {
        if (loginCompleted || exchangeStarted || url.isBlank()) return false
        if (!url.startsWith(CodexOAuth.REDIRECT_URI)) return false
        val uri = Uri.parse(url)
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            Log.e(TAG, "oauth error=$error desc=${uri.getQueryParameter("error_description")}")
            host.finishCanceled()
            return true
        }
        val state = uri.getQueryParameter("state").orEmpty()
        val code = uri.getQueryParameter("code").orEmpty()
        if (state != expectedState || code.isBlank()) {
            Log.e(TAG, "oauth callback missing code/state mismatch")
            host.finishCanceled()
            return true
        }
        exchangeStarted = true
        scope.launch {
            try {
                val bundle = withContext(Dispatchers.IO) {
                    oauth.exchangeCode(code = code, codeVerifier = codeVerifier)
                }
                if (host.isFinishingOrDestroyed) return@launch
                loginCompleted = true
                Log.i(
                    TAG,
                    "login complete account=${bundle.accountId.take(8)}… " +
                        "plan=${bundle.planType} emailLen=${bundle.email.length}",
                )
                host.finishWithResult(
                    Activity.RESULT_OK,
                    Intent()
                        .putExtra(EXTRA_ACCESS_TOKEN, bundle.accessToken)
                        .putExtra(EXTRA_REFRESH_TOKEN, bundle.refreshToken)
                        .putExtra(EXTRA_ID_TOKEN, bundle.idToken)
                        .putExtra(EXTRA_ACCOUNT_ID, bundle.accountId)
                        .putExtra(EXTRA_EXPIRES_AT, bundle.expiresAtEpochMs)
                        .putExtra(EXTRA_EMAIL, bundle.email)
                        .putExtra(EXTRA_PLAN_TYPE, bundle.planType),
                )
            } catch (e: Exception) {
                Log.e(TAG, "token exchange failed: ${e.message}", e)
                if (!host.isFinishingOrDestroyed) {
                    host.finishCanceled()
                }
            }
        }
        return true
    }

    companion object {
        const val FLOW_ID = "codex_login"

        const val EXTRA_ACCESS_TOKEN = "extra_codex_access_token"
        const val EXTRA_REFRESH_TOKEN = "extra_codex_refresh_token"
        const val EXTRA_ID_TOKEN = "extra_codex_id_token"
        const val EXTRA_ACCOUNT_ID = "extra_codex_account_id"
        const val EXTRA_EXPIRES_AT = "extra_codex_expires_at"
        const val EXTRA_EMAIL = "extra_codex_email"
        const val EXTRA_PLAN_TYPE = "extra_codex_plan_type"

        private const val EXTRA_OAUTH_STATE = "extra_codex_oauth_state"
        private const val EXTRA_CODE_VERIFIER = "extra_codex_code_verifier"

        private const val TAG = "CodexLogin"

        fun createIntent(context: Context): Intent {
            val session = CodexOAuth().createPkceSession()
            return InAppWebViewActivity.createIntent(
                context = context,
                url = session.authorizeUrl,
                title = context.getString(R.string.codex_login_title),
                flowId = FLOW_ID,
            ).putExtra(EXTRA_OAUTH_STATE, session.state)
                .putExtra(EXTRA_CODE_VERIFIER, session.codeVerifier)
        }

        fun fromIntent(host: InAppWebViewHost, intent: Intent): CodexLoginWebViewFlow? {
            val state = intent.getStringExtra(EXTRA_OAUTH_STATE).orEmpty()
            val verifier = intent.getStringExtra(EXTRA_CODE_VERIFIER).orEmpty()
            if (state.isBlank() || verifier.isBlank()) return null
            return CodexLoginWebViewFlow(host, expectedState = state, codeVerifier = verifier)
        }
    }
}
