package com.example.data

import android.content.Context
import com.example.AiCoachService
import com.example.AiProvider

/**
 * Local-only storage for AI provider selection and user-provided API keys in private SharedPreferences.
 * Stores separate keys per provider (Groq default + Gemini backup) so switching providers preserves keys.
 * Never reads or persists Groq model names from SharedPreferences (Groq models are strictly locked in code),
 * and automatically heals any leftover `gsk_` key so it always routes to Groq.
 */
class GeminiApiKeyStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    init {
        // Remove any legacy stored Groq model preference so stale Gemini/other model names can never leak
        if (prefs.contains(LEGACY_KEY_GROQ_MODEL)) {
            prefs.edit().remove(LEGACY_KEY_GROQ_MODEL).apply()
        }
        // Auto-heal: if a Groq key (gsk_...) was previously saved under Gemini slot, migrate it to Groq
        val storedGeminiKey = prefs.getString(KEY_GEMINI_API_KEY, "")?.trim().orEmpty()
        if (storedGeminiKey.startsWith("gsk_")) {
            val existingGroqKey = prefs.getString(KEY_GROQ_API_KEY, "")?.trim().orEmpty()
            prefs.edit().apply {
                if (existingGroqKey.isEmpty()) {
                    putString(KEY_GROQ_API_KEY, storedGeminiKey)
                }
                remove(KEY_GEMINI_API_KEY)
                putString(KEY_SELECTED_PROVIDER, AiProvider.GROQ.name)
            }.apply()
        }
    }

    fun getSelectedProvider(): AiProvider {
        val storedProvider = AiProvider.fromStoredName(
            prefs.getString(KEY_SELECTED_PROVIDER, AiProvider.GROQ.name)
        )
        val storedKeyForProvider = getApiKey(storedProvider)
        if (storedKeyForProvider.startsWith("gsk_")) {
            return AiProvider.GROQ
        }
        // If Gemini is selected with no Gemini key, but a valid Groq (gsk_) key exists, prefer Groq
        if (storedProvider == AiProvider.GEMINI && storedKeyForProvider.isEmpty()) {
            val groqKey = prefs.getString(KEY_GROQ_API_KEY, "")?.trim().orEmpty()
            if (groqKey.startsWith("gsk_")) {
                return AiProvider.GROQ
            }
        }
        return AiCoachService.resolveEffectiveProvider(storedProvider, storedKeyForProvider)
    }

    fun saveSelectedProvider(provider: AiProvider) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, provider.name).apply()
    }

    fun getApiKey(provider: AiProvider = getSelectedProvider()): String {
        val prefKey = prefKeyFor(provider)
        return prefs.getString(prefKey, "")?.trim().orEmpty()
    }

    fun saveApiKey(provider: AiProvider, rawKey: String) {
        val cleaned = rawKey.trim()
        val effectiveProvider = if (cleaned.isNotEmpty()) {
            AiCoachService.resolveEffectiveProvider(provider, cleaned)
        } else {
            provider
        }
        val prefKey = prefKeyFor(effectiveProvider)
        prefs.edit().apply {
            if (cleaned.isEmpty()) {
                remove(prefKey)
            } else {
                putString(prefKey, cleaned)
                putString(KEY_SELECTED_PROVIDER, effectiveProvider.name)
            }
        }.apply()
    }

    fun saveApiKey(rawKey: String) {
        saveApiKey(getSelectedProvider(), rawKey)
    }

    private fun prefKeyFor(provider: AiProvider): String = when (provider) {
        AiProvider.GROQ -> KEY_GROQ_API_KEY
        AiProvider.GEMINI -> KEY_GEMINI_API_KEY
    }

    companion object {
        private const val PREFS_NAME = "anti_mager_gemini_prefs"
        private const val KEY_SELECTED_PROVIDER = "selected_ai_provider"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val LEGACY_KEY_GROQ_MODEL = "groq_model_name"
    }
}
