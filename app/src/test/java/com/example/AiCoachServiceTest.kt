package com.example

import com.example.domain.CoachingEngine
import com.example.domain.DateTimeHelper
import com.example.model.SessionSource
import com.example.model.SportType
import com.example.model.TodayStatus
import com.example.model.WorkoutSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCoachServiceTest {

    @Test
    fun groqIsDefaultProviderWithOpenAiGptOssModels() {
        assertEquals(AiProvider.GROQ, AiProvider.fromStoredName(null))
        assertEquals(AiProvider.GROQ, AiProvider.fromStoredName(""))
        assertEquals(AiProvider.GROQ, AiProvider.fromStoredName("UNKNOWN"))
        assertEquals(AiProvider.GROQ, AiProvider.fromStoredName("groq"))
        assertEquals(AiProvider.GEMINI, AiProvider.fromStoredName("GEMINI"))

        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            AiCoachService.GROQ_ENDPOINT
        )
        assertEquals(
            "https://api.groq.com/openai/v1/models",
            AiCoachService.GROQ_MODELS_ENDPOINT
        )
        assertEquals("openai/gpt-oss-20b", AiCoachService.GROQ_MODEL_NAME)
        assertEquals("openai/gpt-oss-120b", AiCoachService.GROQ_FALLBACK_MODEL_NAME)
        assertEquals("openai/gpt-oss-20b", AiProvider.GROQ.modelSummary)
        assertEquals("gemini-3.8-flash", AiProvider.GEMINI.modelSummary)
        assertEquals(2048, AiCoachService.GROQ_MAX_TOKENS)
    }

    @Test
    fun dailyUserPromptIncludesJsonSchemaAndRecentHistory() {
        val nowMillis = 1_750_000_000_000L
        val session = WorkoutSession(
            id = "s1",
            startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 86_400_000L),
            sportType = SportType.RUNNING,
            durationMin = 25,
            rpe = 6,
            notes = "Jogging sore",
            source = SessionSource.MANUAL
        )

        val prompt = AiCoachService.buildDailyUserPrompt(
            sessions = listOf(session),
            energyState = TodayStatus.FRESH,
            nowMillis = nowMillis
        )

        assertTrue(prompt.contains("\"insightBadge\""))
        assertTrue(prompt.contains("\"ideal\""))
        assertTrue(prompt.contains("\"light\""))
        assertTrue(prompt.contains("Jogging sore"))
        assertTrue(prompt.contains(TodayStatus.FRESH.label))
    }

    @Test
    fun weeklyUserPromptIncludes7DayStatsAndThreePointFormat() {
        val nowMillis = 1_750_000_000_000L
        val session = WorkoutSession(
            id = "s2",
            startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 2 * 86_400_000L),
            sportType = SportType.DUMBBELL,
            durationMin = 30,
            rpe = 5,
            source = SessionSource.MANUAL
        )

        val prompt = AiCoachService.buildWeeklyUserPrompt(
            sessions = listOf(session),
            nowMillis = nowMillis
        )

        assertTrue(prompt.contains("Total durasi aktif: 30 menit"))
        assertTrue(prompt.contains("MAKSIMAL 3 poin"))
        assertTrue(prompt.contains("Apresiasi minggu ini"))
    }

    @Test
    fun emptyApiKeyTriggersLocalFallbackForBothProviders() = runBlocking {
        val groqDaily = AiCoachService.generateDailySuggestionsDetailed(
            provider = AiProvider.GROQ,
            apiKey = "   ",
            sessions = emptyList(),
            energyState = TodayStatus.FRESH
        )
        assertTrue(groqDaily is DailySuggestionsResult.FallbackToLocal)
        assertTrue((groqDaily as DailySuggestionsResult.FallbackToLocal).friendlyMessage.contains("Groq"))

        val geminiWeekly = AiCoachService.generateWeeklyReview(
            provider = AiProvider.GEMINI,
            apiKey = "",
            sessions = emptyList()
        )
        assertTrue(geminiWeekly is WeeklyReviewResult.Error)
        assertTrue((geminiWeekly as WeeklyReviewResult.Error).message.contains("Gemini"))
    }

    @Test
    fun parsedRecommendationReflectsGroqProviderLabel() {
        val insights = CoachingEngine.analyzeInsights(emptyList())
        val rec = GeminiCoachService.buildDailyRecommendationFromParsed(
            insightBadge = "⚡ Siap gerak santai",
            ideal = GeminiCoachService.ParsedAiOption(
                title = "Jalan Cepat 20 Menit",
                sportType = "WALKING",
                durationMin = 20,
                reason = "Cocok untuk memulai ritme minggu ini.",
                guidance = "Nikmati langkah santai tanpa terburu-buru."
            ),
            light = GeminiCoachService.ParsedAiOption(
                title = "Peregangan Ringan 10 Menit",
                sportType = "REST",
                durationMin = 10,
                reason = "Tetap aktif tanpa rasa berat.",
                guidance = "Fokus pada napas dalam."
            ),
            energyState = TodayStatus.BUSY,
            insights = insights,
            providerLabel = AiProvider.GROQ.badgeLabel
        )

        assertNotNull(rec)
        assertTrue(rec!!.isAiGenerated)
        assertTrue(rec.contextSummary.contains("Groq AI"))
    }

    @Test
    fun gskApiKeyForcesGroqRoutingEvenWhenGeminiSelected() {
        assertEquals(
            AiProvider.GROQ,
            AiCoachService.resolveEffectiveProvider(AiProvider.GEMINI, "gsk_test123456")
        )
        assertEquals(
            AiProvider.GROQ,
            AiCoachService.resolveEffectiveProvider(AiProvider.GEMINI, "  gsk_with_spaces  ")
        )
        assertEquals(
            AiProvider.GEMINI,
            AiCoachService.resolveEffectiveProvider(AiProvider.GEMINI, "AIzaSyExampleKey")
        )
    }

    @Test
    fun sanitizeGroqModelMigratesLegacyLlamaAndBlocksBlacklistedModels() {
        assertEquals("openai/gpt-oss-20b", AiCoachService.sanitizeGroqModel(null))
        assertEquals("openai/gpt-oss-20b", AiCoachService.sanitizeGroqModel(""))
        assertEquals("openai/gpt-oss-20b", AiCoachService.sanitizeGroqModel("   "))
        // Legacy Llama models auto-migrate to openai/gpt-oss-20b
        assertEquals("openai/gpt-oss-20b", AiCoachService.sanitizeGroqModel("llama-3.3-70b-versatile"))
        assertEquals("openai/gpt-oss-20b", AiCoachService.sanitizeGroqModel("llama-3.1-8b-instant"))
        // Blacklisted models rejected
        assertEquals("openai/gpt-oss-20b", AiCoachService.sanitizeGroqModel("canopylabs/orpheus-arabic-saudi"))
        assertEquals("openai/gpt-oss-20b", AiCoachService.sanitizeGroqModel("whisper-large-v3"))
        assertEquals("openai/gpt-oss-20b", AiCoachService.sanitizeGroqModel("playai-tts"))
        assertEquals("openai/gpt-oss-20b", AiCoachService.sanitizeGroqModel("gemini-3.8-flash"))
        // Valid OpenAI OSS and custom models preserved
        assertEquals("openai/gpt-oss-20b", AiCoachService.sanitizeGroqModel("openai/gpt-oss-20b"))
        assertEquals("openai/gpt-oss-120b", AiCoachService.sanitizeGroqModel("openai/gpt-oss-120b"))
        assertEquals("qwen/qwen3-32b", AiCoachService.sanitizeGroqModel("qwen/qwen3-32b"))
    }

    @Test
    fun buildGroqRequestJsonUsesMaxTokens2048AndOmitsResponseFormat() {
        val body = AiCoachService.buildGroqRequestJson(
            model = "openai/gpt-oss-20b",
            systemPrompt = "System prompt",
            userPrompt = "User prompt"
        )
        assertTrue(body.contains("\"model\":\"openai/gpt-oss-20b\""))
        assertTrue(body.contains("\"max_tokens\":2048"))
        assertFalse(body.contains("response_format"))

        val backupBody = AiCoachService.buildGroqRequestJson(
            model = "openai/gpt-oss-120b",
            systemPrompt = "System prompt",
            userPrompt = "User prompt"
        )
        assertTrue(backupBody.contains("\"model\":\"openai/gpt-oss-120b\""))
        assertTrue(backupBody.contains("\"max_tokens\":2048"))
        assertFalse(backupBody.contains("response_format"))
    }

    @Test
    fun reasoningParserStripsThinkBlocksFencesAndExtractsOuterJsonObject() {
        val rawGroqResponse = """
            {
              "choices": [
                {
                  "message": {
                    "content": "<think>\nAnalisis riwayat latihan 14 hari...\nPeriksa {contoh} dalam pikiran.\n</think>\nBerikut rekomendasimu:\n```json\n{\"insightBadge\":\"✨ Siap gerak\",\"ideal\":{\"title\":\"Jalan Santai\"},\"light\":{\"title\":\"Stretching\"}}\n```\nSemoga membantu!"
                  }
                }
              ]
            }
        """.trimIndent()

        val extractedJson = AiCoachService.extractGroqContent(
            rawResponse = rawGroqResponse,
            extractJsonObject = true
        )
        assertNotNull(extractedJson)
        assertTrue(extractedJson!!.startsWith("{"))
        assertTrue(extractedJson.endsWith("}"))
        assertFalse(extractedJson.contains("<think>"))
        assertFalse(extractedJson.contains("```"))
        assertTrue(extractedJson.contains("\"insightBadge\":\"✨ Siap gerak\""))

        val rawWeeklyText = "<think>Susun 3 poin evaluasi...</think>\n🎯 1. Keren, 3 sesi minggu ini!"
        val cleanedWeekly = AiCoachService.cleanReasoningText(rawWeeklyText)
        assertEquals("🎯 1. Keren, 3 sesi minggu ini!", cleanedWeekly)
    }

    @Test
    fun groqFallbackTries20bThen120bAndSavesActiveModelWithoutCallingModelsEndpoint() = runBlocking {
        val calledModels = mutableListOf<String>()
        var modelsEndpointCalls = 0
        var savedModel: String? = null

        val result = AiCoachService.executeGroqWithFallback(
            preferredModel = "openai/gpt-oss-20b",
            backupModel = "openai/gpt-oss-120b",
            delayFn = {},
            fetchAvailableModels = {
                modelsEndpointCalls++
                listOf("qwen/qwen3-32b")
            },
            onActiveModelChanged = { savedModel = it },
            invoker = { model ->
                calledModels.add(model)
                if (model == "openai/gpt-oss-20b") {
                    AiCoachService.GroqAttemptResult.ModelFailure(
                        statusCode = 404,
                        userMessage = AiCoachService.formatHttpDiagnostic(
                            statusCode = 404,
                            endpointUrl = AiCoachService.GROQ_ENDPOINT,
                            sentModel = model,
                            serverErrorMessage = "Model not found"
                        )
                    )
                } else {
                    AiCoachService.GroqAttemptResult.Success("{\"ok\":true}")
                }
            }
        )

        assertEquals(listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b"), calledModels)
        assertEquals(0, modelsEndpointCalls)
        assertEquals("openai/gpt-oss-120b", savedModel)
        assertTrue(result is GeminiCoachService.GeminiHttpResult.Success)
        assertEquals(
            "openai/gpt-oss-120b",
            (result as GeminiCoachService.GeminiHttpResult.Success).modelUsed
        )
    }

    @Test
    fun groqFallbackCallsModelsEndpointOnlyWhenBothPrimaryAndBackupFailWithModelErrorAndFiltersStrictly() = runBlocking {
        val calledModels = mutableListOf<String>()
        var modelsEndpointCalls = 0
        var savedModel: String? = null

        val result = AiCoachService.executeGroqWithFallback(
            preferredModel = "openai/gpt-oss-20b",
            backupModel = "openai/gpt-oss-120b",
            delayFn = {},
            fetchAvailableModels = {
                modelsEndpointCalls++
                listOf(
                    "canopylabs/orpheus-arabic-saudi",
                    "whisper-large-v3",
                    "playai-tts",
                    "meta-llama/llama-guard-4-12b",
                    "meta-llama/llama-prompt-guard-2-86m",
                    "groq/compound",
                    "allam-2-7b",
                    "llama-3.2-11b-vision-preview",
                    "openai/gpt-oss-safeguard-20b",
                    "deepseek-r1-distill-llama-70b",
                    "qwen/qwen3-32b"
                )
            },
            onActiveModelChanged = { savedModel = it },
            invoker = { model ->
                calledModels.add(model)
                if (model == "openai/gpt-oss-20b" || model == "openai/gpt-oss-120b") {
                    AiCoachService.GroqAttemptResult.ModelFailure(
                        statusCode = 404,
                        userMessage = AiCoachService.formatHttpDiagnostic(
                            statusCode = 404,
                            endpointUrl = AiCoachService.GROQ_ENDPOINT,
                            sentModel = model,
                            serverErrorMessage = "The model does not exist"
                        )
                    )
                } else {
                    AiCoachService.GroqAttemptResult.Success("{\"ok\":true}")
                }
            }
        )

        // qwen has higher whitelist priority than deepseek; all blacklisted models ignored
        assertEquals(1, modelsEndpointCalls)
        assertEquals(
            listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3-32b"),
            calledModels
        )
        assertEquals("qwen/qwen3-32b", savedModel)
        assertTrue(result is GeminiCoachService.GeminiHttpResult.Success)
    }

    @Test
    fun groqRateLimit429FallsBackTo120bWithoutCallingModelsEndpoint() = runBlocking {
        val calledModels = mutableListOf<String>()
        var modelsEndpointCalls = 0

        val result = AiCoachService.executeGroqWithFallback(
            preferredModel = "openai/gpt-oss-20b",
            backupModel = "openai/gpt-oss-120b",
            maxRetries = 1,
            delayFn = {},
            fetchAvailableModels = {
                modelsEndpointCalls++
                listOf("qwen/qwen3-32b")
            },
            invoker = { model ->
                calledModels.add(model)
                AiCoachService.GroqAttemptResult.RetryableFailure(
                    statusCode = 429,
                    userMessage = AiCoachService.formatHttpDiagnostic(
                        statusCode = 429,
                        endpointUrl = AiCoachService.GROQ_ENDPOINT,
                        sentModel = model,
                        serverErrorMessage = "Rate limit exceeded"
                    )
                )
            }
        )

        assertEquals(0, modelsEndpointCalls)
        assertTrue(calledModels.contains("openai/gpt-oss-20b"))
        assertTrue(calledModels.contains("openai/gpt-oss-120b"))
        assertTrue(result is GeminiCoachService.GeminiHttpResult.Failure)
        val message = (result as GeminiCoachService.GeminiHttpResult.Failure).userMessage
        assertTrue(message.contains("HTTP 429"))
        assertTrue(message.contains("Rate limit exceeded"))
        assertTrue(message.contains("openai/gpt-oss-20b"))
        assertTrue(message.contains("openai/gpt-oss-120b"))
    }

    @Test
    fun selectBestFallbackModelsEnforcesStrictBlacklistAndWhitelistPriority() {
        val candidates = AiCoachService.selectBestFallbackModelsFromListing(
            modelIds = listOf(
                "canopylabs/orpheus-v1-english",
                "whisper-large-v3-turbo",
                "playai-tts-arabic",
                "groq/compound-mini",
                "meta-llama/llama-guard-4-12b",
                "meta-llama/llama-prompt-guard-2-22m",
                "nomic-embed-text",
                "cohere-rerank-v3",
                "text-moderation-latest",
                "openai/gpt-oss-safeguard-20b",
                "speech-to-text-v1",
                "audio-preview-1",
                "qwen-vision-7b",
                "unknown-custom-model",
                "meta-llama/llama-4-scout-17b-16e-instruct",
                "mistral-saba-24b",
                "gemma2-9b-it",
                "moonshotai/kimi-k2-instruct",
                "deepseek-r1-distill-qwen-32b",
                "qwen/qwen3-32b",
                "openai/gpt-oss-20b",
                "openai/gpt-oss-120b"
            ),
            alreadyTried = setOf("openai/gpt-oss-20b")
        )

        assertEquals(
            listOf(
                "openai/gpt-oss-120b",
                "deepseek-r1-distill-qwen-32b", // contains "qwen" (tier 1)
                "qwen/qwen3-32b",
                "moonshotai/kimi-k2-instruct",
                "gemma2-9b-it",
                "mistral-saba-24b",
                "meta-llama/llama-4-scout-17b-16e-instruct"
            ),
            candidates
        )
    }

    @Test
    fun groqNon200DiagnosticContainsHttpCodeDomainPathModelAndServerMessage() = runBlocking {
        val diagnostic = AiCoachService.formatHttpDiagnostic(
            statusCode = 404,
            endpointUrl = AiCoachService.GROQ_ENDPOINT,
            sentModel = AiCoachService.GROQ_MODEL_NAME,
            serverErrorMessage = "The model `foo` does not exist"
        )
        assertTrue(diagnostic.contains("HTTP 404"))
        assertTrue(diagnostic.contains("api.groq.com/openai/v1/chat/completions"))
        assertTrue(diagnostic.contains("[Model terkirim: openai/gpt-oss-20b]"))
        assertTrue(diagnostic.contains("The model `foo` does not exist"))

        val result = AiCoachService.executeGroqWithFallback(
            preferredModel = "canopylabs/orpheus-arabic-saudi",
            delayFn = {},
            invoker = { model ->
                AiCoachService.GroqAttemptResult.NonRetryableFailure(
                    AiCoachService.formatHttpDiagnostic(
                        statusCode = 401,
                        endpointUrl = AiCoachService.GROQ_ENDPOINT,
                        sentModel = model,
                        serverErrorMessage = "Invalid API key"
                    )
                )
            }
        )

        assertTrue(result is GeminiCoachService.GeminiHttpResult.Failure)
        val message = (result as GeminiCoachService.GeminiHttpResult.Failure).userMessage
        assertTrue(message.contains("HTTP 401"))
        assertTrue(message.contains("api.groq.com/openai/v1/chat/completions"))
        assertTrue(message.contains("[Model terkirim: openai/gpt-oss-20b]"))
        assertTrue(message.contains("Invalid API key"))
    }

    @Test
    fun extractServerErrorMessageReadsGroqGeminiAndPlainTextErrors() {
        val groqErr = """{"error":{"message":"The model `old-model` does not exist","type":"invalid_request_error"}}"""
        assertEquals(
            "The model `old-model` does not exist",
            AiCoachService.extractServerErrorMessage(groqErr)
        )
        assertEquals(
            "404 page not found",
            AiCoachService.extractServerErrorMessage("<html><body>404 page not found</body></html>")
        )
    }
}
