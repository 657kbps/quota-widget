package com.kuyermqi.quotawidget.domain.antigravity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.antigravity.AntigravityClient
import com.kuyermqi.quotawidget.antigravity.AntigravityLoginSession
import com.kuyermqi.quotawidget.domain.session.SessionLoginResult
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.SessionAuthMethod
import com.kuyermqi.quotawidget.webview.CallbackWebLoginFlow
import com.kuyermqi.quotawidget.webview.InAppWebViewActivity
import com.kuyermqi.quotawidget.webview.InAppWebViewHost
import io.ktor.client.HttpClient

/**
 * Antigravity's Google OAuth (PKCE). Google redirects to a loopback URL that
 * would be a local server in the desktop app; here the WebView captures the
 * code and exchanges it directly.
 */
class AntigravityLoginWebViewFlow private constructor(
    host: InAppWebViewHost,
    httpClient: HttpClient,
    private val settingsRepository: PlatformSettingsRepository,
    private val session: AntigravityLoginSession,
) : CallbackWebLoginFlow(
    host = host,
    callbackMatcher = { uri ->
        (uri.host == "localhost" || uri.host == "127.0.0.1") &&
            uri.path.orEmpty() == "/oauth-callback"
    },
) {
    private val client = AntigravityClient(httpClient)

    override fun onPrepare() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    override suspend fun onCallback(uri: Uri) {
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            finishFailure("AntigravityLogin", "Antigravity 授权失败: $error")
            return
        }
        if (uri.getQueryParameter("state").orEmpty() != session.state) {
            finishFailure("AntigravityLogin", "Antigravity state 校验失败")
            return
        }
        val code = uri.getQueryParameter("code").orEmpty()
        if (code.isBlank()) {
            finishFailure("AntigravityLogin", "Antigravity 未返回授权码")
            return
        }
        val auth = client.exchangeCode(
            code = code,
            codeVerifier = session.codeVerifier,
            redirectUri = session.redirectUri,
        )
        val previous = settingsRepository.getSessionSettings(PlatformIds.ANTIGRAVITY)
        val saved = client.finishLogin(auth, previous) { next ->
            settingsRepository.saveSessionSettings(PlatformIds.ANTIGRAVITY, next)
        }
        finishSuccess(
            SessionLoginResult(
                accessToken = saved.accessToken,
                refreshToken = saved.refreshToken,
                expiresAtEpochMs = saved.expiresAtEpochMs,
                authMethod = SessionAuthMethod.WEB,
                accountId = saved.email,
                email = saved.email,
                plan = saved.plan,
                projectId = saved.projectId,
            ),
        )
    }

    companion object {
        const val FLOW_ID = "antigravity_login"
        private const val EXTRA_STATE = "extra_antigravity_state"
        private const val EXTRA_VERIFIER = "extra_antigravity_verifier"
        private const val EXTRA_REDIRECT = "extra_antigravity_redirect"

        fun createIntent(context: Context): Intent {
            val session = AntigravityClient.createLoginSession()
            return InAppWebViewActivity.createIntent(
                context = context,
                url = session.authorizeUrl,
                title = context.getString(
                    R.string.session_login_title,
                    context.getString(R.string.platform_name_antigravity),
                ),
                flowId = FLOW_ID,
                userAgent = GOOGLE_WEBVIEW_USER_AGENT,
            ).putExtra(EXTRA_STATE, session.state)
                .putExtra(EXTRA_VERIFIER, session.codeVerifier)
                .putExtra(EXTRA_REDIRECT, session.redirectUri)
        }

        fun fromIntent(
            host: InAppWebViewHost,
            httpClient: HttpClient,
            intent: Intent,
            settingsRepository: PlatformSettingsRepository,
        ): AntigravityLoginWebViewFlow? {
            val state = intent.getStringExtra(EXTRA_STATE).orEmpty()
            val verifier = intent.getStringExtra(EXTRA_VERIFIER).orEmpty()
            val redirect = intent.getStringExtra(EXTRA_REDIRECT).orEmpty()
            if (state.isBlank() || verifier.isBlank() || redirect.isBlank()) return null
            return AntigravityLoginWebViewFlow(
                host = host,
                httpClient = httpClient,
                settingsRepository = settingsRepository,
                session = AntigravityLoginSession(
                    state = state,
                    codeVerifier = verifier,
                    authorizeUrl = "",
                    redirectUri = redirect,
                ),
            )
        }

        private const val GOOGLE_WEBVIEW_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
