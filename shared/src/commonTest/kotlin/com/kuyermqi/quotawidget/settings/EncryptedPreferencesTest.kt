package com.kuyermqi.quotawidget.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptedPreferencesTest {
    @Test
    fun detectsStoredCiphertextKeys() {
        val ciphertextNames = listOf(
            "deepseek_api_key_enc",
            "opencode_auth_cookie_enc",
            "opencode_api_key_enc",
            "codex_access_token_enc",
            "codex_refresh_token_enc",
            "codex_id_token_enc",
            "new_api_key_enc",
            "magpie_key_enc",
            "kimi_code_api_key_enc",
            "zhipu_api_key_enc",
            "session_codex_access_enc",
            "session_codex_refresh_enc",
            "session_codex_api_key_enc",
        )

        ciphertextNames.forEach { name ->
            assertTrue(isEncryptedPreferenceName(name), name)
        }
    }

    @Test
    fun ignoresPlaintextKeys() {
        val plaintextNames = listOf(
            "deepseek_currency",
            "opencode_auth_method",
            "codex_account_id",
            "codex_access_token",
            "widget_deepseek_status",
            "session_codex_expires_at",
            "encode",
            "",
        )

        plaintextNames.forEach { name ->
            assertFalse(isEncryptedPreferenceName(name), name)
        }
    }
}
