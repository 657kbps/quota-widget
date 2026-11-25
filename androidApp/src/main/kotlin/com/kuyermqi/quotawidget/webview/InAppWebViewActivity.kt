package com.kuyermqi.quotawidget.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.antigravity.AntigravityLoginWebViewFlow
import com.kuyermqi.quotawidget.domain.cline.ClineLoginWebViewFlow
import com.kuyermqi.quotawidget.domain.codex.CodexLoginWebViewFlow
import com.kuyermqi.quotawidget.domain.commandcode.CommandCodeLoginWebViewFlow
import com.kuyermqi.quotawidget.domain.opencode.OpenCodeLoginWebViewFlow
import com.kuyermqi.quotawidget.domain.workbuddy.WorkBuddyLoginWebViewFlow
import com.kuyermqi.quotawidget.locale.LocaleAwareActivity
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.theme.AppNightMode
import com.kuyermqi.quotawidget.ui.theme.QuotaWidgetTheme

/**
 * Generic in-app browser shell. Platform login / capture logic plugs in via [InAppWebViewFlow]
 * selected by [EXTRA_FLOW_ID].
 */
class InAppWebViewActivity : LocaleAwareActivity(), InAppWebViewHost {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var flow: InAppWebViewFlow? = null
    private var webView: WebView? = null
    private var webViewReleased = false

    override val isFinishingOrDestroyed: Boolean
        get() = isFinishing || isDestroyed

    override fun postDelayed(delayMs: Long, action: () -> Unit) {
        mainHandler.postDelayed(action, delayMs)
    }

    override fun cancelPendingPosts() {
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun finishWithResult(resultCode: Int, data: Intent?) {
        if (data != null) setResult(resultCode, data) else setResult(resultCode)
        finish()
    }

    override fun finishCanceled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as QuotaWidgetApp
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val flowId = intent.getStringExtra(EXTRA_FLOW_ID).orEmpty()
        val userAgent = intent.getStringExtra(EXTRA_USER_AGENT).orEmpty()
        val createdFlow = createFlow(flowId, app.settingsRepository)
        if (flowId.isNotBlank() && createdFlow == null) {
            finishAndReleaseFlow()
            return
        }
        val activeFlow = createdFlow.orPassthrough()
        flow = activeFlow
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
            .ifBlank { activeFlow.initialUrl().orEmpty() }
        if (url.isBlank()) {
            finishAndReleaseFlow()
            return
        }
        activeFlow.onPrepare()
        setContent {
            val context = LocalContext.current
            val initialSettings = remember {
                AppSettings(darkThemeMode = AppNightMode.storedMode(context))
            }
            val appSettings by app.settingsRepository.observeAppSettings()
                .collectAsStateWithLifecycle(initialValue = initialSettings)

            QuotaWidgetTheme(
                darkThemeMode = appSettings.darkThemeMode,
                themeColorMode = appSettings.themeColorMode,
                seedColor = Color(appSettings.customSeedColorArgb),
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = title.ifBlank { stringResource(R.string.app_name) },
                                    maxLines = 1,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrow_back),
                                        contentDescription = stringResource(R.string.in_app_webview_back),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { padding ->
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                if (userAgent.isNotBlank()) {
                                    settings.userAgentString = userAgent
                                }
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                // Keep page prefers-color-scheme; avoid WebView forcing dark paint.
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    @Suppress("DEPRECATION")
                                    settings.forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    settings.isAlgorithmicDarkeningAllowed = false
                                }
                                this@InAppWebViewActivity.webView = this
                                this@InAppWebViewActivity.webViewReleased = false
                                webViewClient = LayoutFixingWebViewClient(activeFlow.createWebViewClient())
                                loadUrl(url)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        onRelease = ::releaseWebView,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        cancelPendingPosts()
        flow?.onDestroy()
        flow = null
        webView?.let(::releaseWebView)
        super.onDestroy()
    }

    /**
     * Releases the flow (loopback server / HTTP client / scope) before finishing, so an early exit
     * during [onCreate] does not leak resources or rely on a later [onDestroy].
     */
    private fun finishAndReleaseFlow() {
        flow?.onDestroy()
        flow = null
        finish()
    }

    private fun releaseWebView(view: WebView) {
        if (webViewReleased) return
        webViewReleased = true
        webView = null
        runCatching { view.stopLoading() }
        view.webViewClient = WebViewClient()
        runCatching { (view.parent as? ViewGroup)?.removeView(view) }
        runCatching { view.destroy() }
    }

    private fun createFlow(
        flowId: String,
        settingsRepository: PlatformSettingsRepository,
    ): InAppWebViewFlow? =
        when (flowId) {
            OpenCodeLoginWebViewFlow.FLOW_ID -> OpenCodeLoginWebViewFlow(this)
            CodexLoginWebViewFlow.FLOW_ID -> CodexLoginWebViewFlow.fromIntent(this, intent)
            WorkBuddyLoginWebViewFlow.FLOW_ID -> WorkBuddyLoginWebViewFlow.fromIntent(this, intent)
            ClineLoginWebViewFlow.FLOW_ID -> ClineLoginWebViewFlow.fromIntent(this, intent)
            CommandCodeLoginWebViewFlow.FLOW_ID -> CommandCodeLoginWebViewFlow.fromIntent(this, intent)
            AntigravityLoginWebViewFlow.FLOW_ID ->
                AntigravityLoginWebViewFlow.fromIntent(this, intent, settingsRepository)
            else -> null
        }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FLOW_ID = "extra_flow_id"
        const val EXTRA_USER_AGENT = "extra_user_agent"

        fun createIntent(
            context: Context,
            url: String,
            title: String,
            flowId: String? = null,
            userAgent: String? = null,
        ): Intent =
            Intent(context, InAppWebViewActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .apply {
                    if (!flowId.isNullOrBlank()) {
                        putExtra(EXTRA_FLOW_ID, flowId)
                    }
                    if (!userAgent.isNullOrBlank()) {
                        putExtra(EXTRA_USER_AGENT, userAgent)
                    }
                }
    }
}

/**
 * Applies viewport layout fix, then forwards navigation callbacks to the flow client.
 * Must forward [shouldOverrideUrlLoading] so OAuth flows can intercept localhost redirects
 * without starting a local callback server.
 */
private class LayoutFixingWebViewClient(
    private val delegate: WebViewClient,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean = delegate.shouldOverrideUrlLoading(view, request)

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        @Suppress("DEPRECATION")
        delegate.shouldOverrideUrlLoading(view, url)

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        delegate.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        view?.injectFullViewportLayoutFix()
        delegate.onPageFinished(view, url)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        delegate.doUpdateVisitedHistory(view, url, isReload)
    }
}
