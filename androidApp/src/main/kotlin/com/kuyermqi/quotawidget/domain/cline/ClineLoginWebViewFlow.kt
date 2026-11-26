package com.kuyermqi.quotawidget.domain.cline

import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.cline.ClineClient
import com.kuyermqi.quotawidget.cline.ClineDevicePoll
import com.kuyermqi.quotawidget.cline.ClineDeviceSession
import com.kuyermqi.quotawidget.domain.session.SessionLoginResult
import com.kuyermqi.quotawidget.settings.SessionAuthMethod
import com.kuyermqi.quotawidget.webview.InAppWebViewActivity
import com.kuyermqi.quotawidget.webview.InAppWebViewHost
import com.kuyermqi.quotawidget.webview.PollingWebLoginFlow
import io.ktor.client.HttpClient

/**
 * Cline's WorkOS device sign-in: the approval page is shown in the WebView
 * while the app polls WorkOS, then trades the WorkOS tokens for Cline's own.
 */
class ClineLoginWebViewFlow private constructor(
    host: InAppWebViewHost,
    httpClient: HttpClient,
    private val session: ClineDeviceSession,
) : PollingWebLoginFlow(
    host = host,
    intervalMs = session.intervalSeconds.coerceAtLeast(1) * 1000L,
    timeoutMs = session.expiresInSeconds.coerceIn(30, 600) * 1000L,
) {
    private val client = ClineClient(httpClient)

    override fun onPrepare() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        super.onPrepare()
    }

    override suspend fun pollOnce(): Boolean {
        return when (val poll = client.pollDeviceToken(session)) {
            ClineDevicePoll.Pending -> false
            ClineDevicePoll.SlowDown -> {
                requestSlowerPolling()
                false
            }
            is ClineDevicePoll.Ready -> {
                val auth = client.register(poll)
                finishSuccess(
                    SessionLoginResult(
                        accessToken = auth.accessToken,
                        refreshToken = auth.refreshToken,
                        expiresAtEpochMs = auth.expiresAtEpochMs,
                        authMethod = SessionAuthMethod.WEB,
                        accountId = auth.email.ifBlank { auth.uid },
                        email = auth.email,
                        uid = auth.uid,
                        plan = auth.plan,
                    ),
                )
                true
            }
        }
    }

    companion object {
        const val FLOW_ID = "cline_login"
        private const val EXTRA_DEVICE_CODE = "extra_cline_device_code"
        private const val EXTRA_VERIFICATION_URL = "extra_cline_verification_url"
        private const val EXTRA_INTERVAL = "extra_cline_interval"
        private const val EXTRA_EXPIRES_IN = "extra_cline_expires_in"

        fun createIntent(context: Context, session: ClineDeviceSession): Intent =
            InAppWebViewActivity.createIntent(
                context = context,
                url = session.verificationUrl,
                title = context.getString(
                    R.string.session_login_title,
                    context.getString(R.string.platform_name_cline),
                ),
                flowId = FLOW_ID,
            ).putExtra(EXTRA_DEVICE_CODE, session.deviceCode)
                .putExtra(EXTRA_VERIFICATION_URL, session.verificationUrl)
                .putExtra(EXTRA_INTERVAL, session.intervalSeconds)
                .putExtra(EXTRA_EXPIRES_IN, session.expiresInSeconds)

        fun fromIntent(
            host: InAppWebViewHost,
            httpClient: HttpClient,
            intent: Intent,
        ): ClineLoginWebViewFlow? {
            val deviceCode = intent.getStringExtra(EXTRA_DEVICE_CODE).orEmpty()
            val verificationUrl = intent.getStringExtra(EXTRA_VERIFICATION_URL).orEmpty()
            if (deviceCode.isBlank() || verificationUrl.isBlank()) return null
            return ClineLoginWebViewFlow(
                host = host,
                httpClient = httpClient,
                session = ClineDeviceSession(
                    deviceCode = deviceCode,
                    verificationUrl = verificationUrl,
                    intervalSeconds = intent.getIntExtra(EXTRA_INTERVAL, 5),
                    expiresInSeconds = intent.getIntExtra(EXTRA_EXPIRES_IN, 300),
                ),
            )
        }
    }
}
