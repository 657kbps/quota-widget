package com.kuyermqi.quotawidget.domain.workbuddy

import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.session.SessionLoginResult
import com.kuyermqi.quotawidget.webview.InAppWebViewActivity
import com.kuyermqi.quotawidget.webview.InAppWebViewHost
import com.kuyermqi.quotawidget.webview.PollingWebLoginFlow
import com.kuyermqi.quotawidget.workbuddy.WorkBuddyClient
import com.kuyermqi.quotawidget.workbuddy.WorkBuddyLoginSession
import com.kuyermqi.quotawidget.workbuddy.WorkBuddyRegion
import io.ktor.client.HttpClient

/**
 * WorkBuddy browser sign-in. The page finishes server-side; this flow polls
 * the plugin auth endpoints until the token and account are available.
 */
class WorkBuddyLoginWebViewFlow private constructor(
    host: InAppWebViewHost,
    httpClient: HttpClient,
    private val region: WorkBuddyRegion,
    private val state: String,
) : PollingWebLoginFlow(
    host = host,
    intervalMs = 1_000L,
    timeoutMs = 5 * 60 * 1000L,
) {
    private val client = WorkBuddyClient(httpClient)
    private var token: com.kuyermqi.quotawidget.workbuddy.WorkBuddyToken? = null

    override fun onPrepare() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        super.onPrepare()
    }

    override suspend fun pollOnce(): Boolean {
        val current = token ?: client.pollLoginToken(region, state)?.also { token = it }
            ?: return false
        val account = client.fetchLoginAccount(region, state, current) ?: return false
        finishSuccess(
            SessionLoginResult(
                accessToken = current.accessToken,
                refreshToken = current.refreshToken,
                expiresAtEpochMs = current.expiresAtEpochMs,
                accountId = buildAccountLabel(account),
                email = account.phoneNumber,
                uid = account.uid,
                domain = current.domain,
                enterpriseId = account.enterpriseId,
                plan = "Free",
            ),
        )
        return true
    }

    private fun buildAccountLabel(account: com.kuyermqi.quotawidget.workbuddy.WorkBuddyAccount): String {
        val person = account.nickname.ifBlank { account.phoneNumber }.ifBlank { account.uid }
        val team = account.enterpriseName
        return when {
            team.isBlank() -> person
            person.isBlank() -> team
            else -> "$person · $team"
        }
    }

    companion object {
        const val FLOW_ID = "workbuddy_login"
        private const val EXTRA_PLATFORM_ID = "extra_workbuddy_platform_id"
        private const val EXTRA_STATE = "extra_workbuddy_state"

        fun createIntent(
            context: Context,
            region: WorkBuddyRegion,
            session: WorkBuddyLoginSession,
        ): Intent = InAppWebViewActivity.createIntent(
            context = context,
            url = session.authUrl,
            title = context.getString(
                R.string.session_login_title,
                context.getString(
                    if (region == WorkBuddyRegion.CHINA) {
                        R.string.platform_name_workbuddy
                    } else {
                        R.string.platform_name_workbuddy_ai
                    },
                ),
            ),
            flowId = FLOW_ID,
        ).putExtra(EXTRA_PLATFORM_ID, region.platformId)
            .putExtra(EXTRA_STATE, session.state)

        fun fromIntent(
            host: InAppWebViewHost,
            httpClient: HttpClient,
            intent: Intent,
        ): WorkBuddyLoginWebViewFlow? {
            val platformId = intent.getStringExtra(EXTRA_PLATFORM_ID).orEmpty()
            val region = WorkBuddyRegion.forPlatform(platformId) ?: return null
            val state = intent.getStringExtra(EXTRA_STATE).orEmpty()
            if (state.isBlank()) return null
            return WorkBuddyLoginWebViewFlow(host, httpClient, region, state)
        }
    }
}