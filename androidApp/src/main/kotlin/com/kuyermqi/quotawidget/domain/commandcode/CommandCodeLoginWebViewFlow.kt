package com.kuyermqi.quotawidget.domain.commandcode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebViewClient
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.commandcode.CommandCodeCallback
import com.kuyermqi.quotawidget.commandcode.CommandCodeClient
import com.kuyermqi.quotawidget.domain.session.SessionLoginResult
import com.kuyermqi.quotawidget.webview.InAppWebViewActivity
import com.kuyermqi.quotawidget.webview.InAppWebViewFlow
import com.kuyermqi.quotawidget.webview.InAppWebViewHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Command Code Studio's browser sign-in. Studio POSTs the new key to a
 * loopback callback (the CLI normally runs that server itself); this flow
 * hosts a small server on the phone and keeps the page in the WebView.
 */
class CommandCodeLoginWebViewFlow private constructor(
    private val host: InAppWebViewHost,
    private val state: String,
) : InAppWebViewFlow {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = CommandCodeClient()
    private var finished = false

    private val server = CommandCodeCallbackServer(
        expectedState = state,
        onCallback = { callback ->
            scope.launch { completeLogin(callback) }
        },
        onFailure = { message -> scope.launch { finishFailure(message) } },
    )

    private val loginUrl: String by lazy {
        server.port.takeIf { it > 0 }?.let { client.createLoginUrl(state, it) }.orEmpty()
    }

    init {
        server.start()
    }

    override fun initialUrl(): String? = loginUrl.ifBlank { null }

    override fun onPrepare() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    override fun createWebViewClient(): WebViewClient = WebViewClient()

    override fun onDestroy() {
        server.close()
        client.close()
        scope.cancel()
    }

    private suspend fun completeLogin(callback: CommandCodeCallback) {
        try {
            val identity = client.fetchIdentity(callback.apiKey)
            if (finished) return
            finished = true
            host.finishWithResult(
                Activity.RESULT_OK,
                SessionLoginResult(
                    apiKey = callback.apiKey,
                    accountId = identity.email.ifBlank { identity.userName },
                    email = identity.email,
                    plan = identity.plan,
                ).toIntent(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Command Code login failed: ${e.message}")
            finishFailure(e.message ?: "Command Code 登录失败")
        }
    }

    private fun finishFailure(message: String) {
        if (finished) return
        finished = true
        Log.e(TAG, message)
        if (!host.isFinishingOrDestroyed) host.finishCanceled()
    }

    companion object {
        const val FLOW_ID = "command_code_login"
        private const val TAG = "CommandCodeLogin"
        private const val EXTRA_STATE = "extra_command_code_state"

        fun createIntent(context: Context): Intent {
            val state = CommandCodeClient.newState()
            return InAppWebViewActivity.createIntent(
                context = context,
                url = "",
                title = context.getString(
                    R.string.session_login_title,
                    context.getString(R.string.platform_name_command_code),
                ),
                flowId = FLOW_ID,
            ).putExtra(EXTRA_STATE, state)
        }

        fun fromIntent(host: InAppWebViewHost, intent: Intent): CommandCodeLoginWebViewFlow? {
            val state = intent.getStringExtra(EXTRA_STATE).orEmpty()
            if (state.isBlank()) return null
            return CommandCodeLoginWebViewFlow(host, state)
        }
    }
}
