package com.kuyermqi.quotawidget.webview

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import com.kuyermqi.quotawidget.domain.session.SessionLoginResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reusable login flow for providers that finish by redirecting to a loopback
 * callback URL. The WebView intercepts that navigation instead of loading it,
 * so a desktop-style "paste the callback URL" step is not needed on Android.
 */
abstract class CallbackWebLoginFlow(
    protected val host: InAppWebViewHost,
    private val callbackMatcher: (Uri) -> Boolean,
) : InAppWebViewFlow {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var handled = false
    private var finished = false

    final override fun createWebViewClient(): WebViewClient =
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean = handle(view, request?.url?.toString().orEmpty())

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                handle(view, url.orEmpty())

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (handle(view, url.orEmpty())) view?.stopLoading()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (handle(view, url.orEmpty())) view?.stopLoading()
            }
        }

    override fun onDestroy() {
        scope.cancel()
        closeResources()
    }

    protected open fun closeResources() {}

    protected abstract suspend fun onCallback(uri: Uri)

    protected fun finishSuccess(result: SessionLoginResult) {
        if (finished) return
        finished = true
        host.finishWithResult(Activity.RESULT_OK, result.toIntent())
    }

    protected fun finishFailure(tag: String, message: String) {
        if (finished) return
        finished = true
        Log.e(tag, message)
        if (!host.isFinishingOrDestroyed) host.finishCanceled()
    }

    private fun handle(view: WebView?, url: String): Boolean {
        if (finished || handled || url.isBlank()) return false
        val uri = runCatching { url.toUri() }.getOrNull() ?: return false
        if (!callbackMatcher(uri)) return false
        handled = true
        view?.stopLoading()
        scope.launch {
            try {
                onCallback(uri)
            } catch (e: Exception) {
                finishFailure(TAG, "callback failed: ${e.message}")
            }
        }
        return true
    }

    private companion object {
        const val TAG = "SessionLogin"
    }
}

/**
 * Reusable login flow for providers whose browser page is only an approval
 * step: the app polls the provider until the sign-in appears server-side.
 */
abstract class PollingWebLoginFlow(
    protected val host: InAppWebViewHost,
    private val intervalMs: Long,
    private val timeoutMs: Long,
) : InAppWebViewFlow {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private var finished = false

    override fun onPrepare() {
        startPolling()
    }

    override fun createWebViewClient(): WebViewClient = WebViewClient()

    override fun onDestroy() {
        scope.cancel()
        closeResources()
    }

    protected open fun closeResources() {}

    /** Returns true once the login has been persisted / handed to [finishSuccess]. */
    protected abstract suspend fun pollOnce(): Boolean

    protected open suspend fun onTimeout() {
        finishFailure("登录超时，请重试")
    }

    protected fun finishSuccess(result: SessionLoginResult) {
        if (finished) return
        finished = true
        host.finishWithResult(Activity.RESULT_OK, result.toIntent())
    }

    protected fun finishFailure(message: String) {
        if (finished) return
        finished = true
        Log.e(TAG, message)
        if (!host.isFinishingOrDestroyed) host.finishCanceled()
    }

    private fun startPolling() {
        if (started) return
        started = true
        scope.launch {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!finished && !host.isFinishingOrDestroyed) {
                val done = try {
                    pollOnce()
                } catch (e: Exception) {
                    Log.e(TAG, "poll failed: ${e.message}", e)
                    finishFailure(e.message ?: "登录失败，请重试")
                    return@launch
                }
                if (done || finished) return@launch
                if (System.currentTimeMillis() >= deadline) {
                    onTimeout()
                    return@launch
                }
                delay(intervalMs)
            }
        }
    }

    private companion object {
        const val TAG = "SessionLogin"
    }
}
