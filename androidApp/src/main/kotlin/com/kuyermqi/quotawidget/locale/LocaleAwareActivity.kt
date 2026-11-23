package com.kuyermqi.quotawidget.locale

import android.content.Context
import androidx.activity.ComponentActivity
import com.kuyermqi.quotawidget.domain.AppLanguage

/** [ComponentActivity] whose resources follow [AppLocale] and that recreates on language change. */
abstract class LocaleAwareActivity : ComponentActivity() {
    private var attachedLanguage: AppLanguage = AppLanguage.FollowSystem

    override fun attachBaseContext(newBase: Context) {
        attachedLanguage = AppLocale.current()
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onStart() {
        super.onStart()
        if (attachedLanguage != AppLocale.current()) {
            recreate()
            return
        }
        AppLocale.register(this)
    }

    override fun onStop() {
        AppLocale.unregister(this)
        super.onStop()
    }
}
