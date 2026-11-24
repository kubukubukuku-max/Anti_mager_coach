package com.example.data

import android.content.Context
import com.example.AiProvider

/**
 * Local-only storage for AI provider selection and user-provided API keys in private SharedPreferences.
 * Stores separate keys per provider (Groq default + Gemini backup) so switching providers preserves keys.
 * Never synced to Firestore and never hardcoded in source code.
 */
class GeminiApiKeyStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getSelectedProvider(): AiProvider {
        return AiProvider.fromStoredName(prefs.getString(KEY_SELECTED_PROVIDER, AiProvider.GROQ.name))
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
        val prefKey = prefKeyFor(provider)
        prefs.edit().apply {
            if (cleaned.isEmpty()) {
                remove(prefKey)
            } else {
                putString(prefKey, cleaned)
            }
        }.apply()
    }

    fun saveApiKey(rawKey: String) {
        saveApiKey(getSelectedProvider(), rawKey)
    }

    fun getGroqModel(): String {
        val stored = prefs.getString(KEY_GROQ_MODEL, null)
        return com.example.AiCoachService.sanitizeGroqModel(stored)
    }

    fun saveGroqModel(rawModel: String?) {
        val safeModel = com.example.AiCoachService.sanitizeGroqModel(rawModel)
        prefs.edit().putString(KEY_GROQ_MODEL, safeModel).apply()
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
        private const val KEY_GROQ_MODEL = "groq_model_name"
    }
}
