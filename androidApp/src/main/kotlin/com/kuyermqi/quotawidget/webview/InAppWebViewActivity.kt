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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
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
import com.kuyermqi.quotawidget.domain.opencode.OpenCodeLoginWebViewFlow
import com.kuyermqi.quotawidget.ui.theme.AppNightMode
import com.kuyermqi.quotawidget.ui.theme.QuotaWidgetTheme

/**
 * Generic in-app browser shell. Platform login / capture logic plugs in via [InAppWebViewFlow]
 * selected by [EXTRA_FLOW_ID].
 */
class InAppWebViewActivity : ComponentActivity(), InAppWebViewHost {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var flow: InAppWebViewFlow

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
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val flowId = intent.getStringExtra(EXTRA_FLOW_ID).orEmpty()
        if (url.isBlank()) {
            finish()
            return
        }
        flow = createFlow(flowId).orPassthrough()
        flow.onPrepare()
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
                                webViewClient = LayoutFixingWebViewClient(flow.createWebViewClient())
                                loadUrl(url)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        cancelPendingPosts()
        flow.onDestroy()
        super.onDestroy()
    }

    private fun createFlow(flowId: String): InAppWebViewFlow? =
        when (flowId) {
            OpenCodeLoginWebViewFlow.FLOW_ID -> OpenCodeLoginWebViewFlow(this)
            else -> null
        }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FLOW_ID = "extra_flow_id"

        fun createIntent(
            context: Context,
            url: String,
            title: String,
            flowId: String? = null,
        ): Intent =
            Intent(context, InAppWebViewActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .apply {
                    if (!flowId.isNullOrBlank()) {
                        putExtra(EXTRA_FLOW_ID, flowId)
                    }
                }
    }
}

/** Applies viewport layout fix, then forwards [onPageFinished] to the flow client. */
private class LayoutFixingWebViewClient(
    private val delegate: WebViewClient,
) : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        view?.injectFullViewportLayoutFix()
        delegate.onPageFinished(view, url)
    }
}
