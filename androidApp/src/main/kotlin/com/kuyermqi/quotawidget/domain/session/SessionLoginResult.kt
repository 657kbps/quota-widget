package com.kuyermqi.quotawidget.domain.session

import android.content.Intent
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings

/** Credentials one session-based provider login returns to the host screen. */
data class SessionLoginResult(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAtEpochMs: Long = 0L,
    val apiKey: String = "",
    val accountId: String = "",
    val email: String = "",
    val plan: String = "",
    val projectId: String = "",
    val domain: String = "",
    val uid: String = "",
    val enterpriseId: String = "",
) {
    fun toIntent(): Intent = Intent()
        .putExtra(EXTRA_ACCESS_TOKEN, accessToken)
        .putExtra(EXTRA_REFRESH_TOKEN, refreshToken)
        .putExtra(EXTRA_EXPIRES_AT, expiresAtEpochMs)
        .putExtra(EXTRA_API_KEY, apiKey)
        .putExtra(EXTRA_ACCOUNT_ID, accountId)
        .putExtra(EXTRA_EMAIL, email)
        .putExtra(EXTRA_PLAN, plan)
        .putExtra(EXTRA_PROJECT_ID, projectId)
        .putExtra(EXTRA_DOMAIN, domain)
        .putExtra(EXTRA_UID, uid)
        .putExtra(EXTRA_ENTERPRISE_ID, enterpriseId)

    fun applyTo(settings: SessionPlatformSettings): SessionPlatformSettings = settings.copy(
        accessToken = accessToken.ifBlank { settings.accessToken },
        refreshToken = refreshToken.ifBlank { settings.refreshToken },
        expiresAtEpochMs = expiresAtEpochMs.takeIf { it > 0L } ?: settings.expiresAtEpochMs,
        apiKey = apiKey.ifBlank { settings.apiKey },
        accountId = accountId.ifBlank { settings.accountId },
        email = email.ifBlank { settings.email },
        plan = plan.ifBlank { settings.plan },
        projectId = projectId.ifBlank { settings.projectId },
        domain = domain.ifBlank { settings.domain },
        uid = uid.ifBlank { settings.uid },
        enterpriseId = enterpriseId.ifBlank { settings.enterpriseId },
    )

    companion object {
        private const val EXTRA_ACCESS_TOKEN = "extra_session_access_token"
        private const val EXTRA_REFRESH_TOKEN = "extra_session_refresh_token"
        private const val EXTRA_EXPIRES_AT = "extra_session_expires_at"
        private const val EXTRA_API_KEY = "extra_session_api_key"
        private const val EXTRA_ACCOUNT_ID = "extra_session_account_id"
        private const val EXTRA_EMAIL = "extra_session_email"
        private const val EXTRA_PLAN = "extra_session_plan"
        private const val EXTRA_PROJECT_ID = "extra_session_project_id"
        private const val EXTRA_DOMAIN = "extra_session_domain"
        private const val EXTRA_UID = "extra_session_uid"
        private const val EXTRA_ENTERPRISE_ID = "extra_session_enterprise_id"

        fun fromIntent(intent: Intent?): SessionLoginResult? {
            if (intent == null) return null
            val result = SessionLoginResult(
                accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty(),
                refreshToken = intent.getStringExtra(EXTRA_REFRESH_TOKEN).orEmpty(),
                expiresAtEpochMs = intent.getLongExtra(EXTRA_EXPIRES_AT, 0L),
                apiKey = intent.getStringExtra(EXTRA_API_KEY).orEmpty(),
                accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID).orEmpty(),
                email = intent.getStringExtra(EXTRA_EMAIL).orEmpty(),
                plan = intent.getStringExtra(EXTRA_PLAN).orEmpty(),
                projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty(),
                domain = intent.getStringExtra(EXTRA_DOMAIN).orEmpty(),
                uid = intent.getStringExtra(EXTRA_UID).orEmpty(),
                enterpriseId = intent.getStringExtra(EXTRA_ENTERPRISE_ID).orEmpty(),
            )
            return result.takeIf {
                it.accessToken.isNotBlank() ||
                    it.refreshToken.isNotBlank() ||
                    it.apiKey.isNotBlank()
            }
        }
    }
}
