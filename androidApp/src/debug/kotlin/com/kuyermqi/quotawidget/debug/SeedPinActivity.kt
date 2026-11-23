package com.kuyermqi.quotawidget.debug

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Debug-only launcher for seeding data and pinning widget instances. */
class SeedPinActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        status = TextView(this).apply {
            text = "未操作"
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }
        root.addView(status)
        addButton(root, "重新写入假数据") { reseed() }
        addButton(root, "OpenCode 2×2 · 单行") {
            pin("com.kuyermqi.quotawidget.widget.OpenCodeGoWidgetReceiver")
        }
        addButton(root, "OpenCode 2×1 · 单行") {
            pin("com.kuyermqi.quotawidget.widget.OpenCodeGoCompactWidgetReceiver")
        }
        addButton(root, "Kimi Code 2×2 · 两行") {
            pin("com.kuyermqi.quotawidget.widget.KimiCodeWidgetReceiver")
        }
        addButton(root, "Magpie 2×2 · 胶囊") {
            pin("com.kuyermqi.quotawidget.widget.MagpieWidgetReceiver")
        }
        addButton(root, "Codex 2×1 · 胶囊进度（回退单行）") {
            pin("com.kuyermqi.quotawidget.widget.CodexCompactWidgetReceiver")
        }
        addButton(root, "OpenCode 总览 · BAR") {
            pin("com.kuyermqi.quotawidget.widget.OpenCodeGoOverviewWidgetReceiver")
        }
        addButton(root, "Zhipu 总览 · CAPSULE") {
            pin("com.kuyermqi.quotawidget.widget.ZhipuOverviewWidgetReceiver")
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun addButton(root: LinearLayout, label: String, onClick: () -> Unit) {
        root.addView(
            Button(this).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                setOnClickListener { onClick() }
            },
        )
    }

    private fun reseed() {
        scope.launch {
            DebugWidgetSeed.seed(applicationContext as QuotaWidgetApp)
            WidgetGlanceState.syncAndUpdate(this@SeedPinActivity, "debug_seed")
            status.text = "假数据已写入"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun pin(receiverName: String) {
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, receiverName)
        val supported = manager.isRequestPinAppWidgetSupported
        val requested = supported && manager.requestPinAppWidget(provider, null, null)
        status.text = "pin $receiverName supported=$supported requested=$requested"
    }
}
