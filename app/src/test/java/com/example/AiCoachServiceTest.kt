package com.example

import com.example.domain.CoachingEngine
import com.example.domain.DateTimeHelper
import com.example.model.SessionSource
import com.example.model.SportType
import com.example.model.TodayStatus
import com.example.model.WorkoutSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCoachServiceTest {

    @Test
    fun groqIsDefaultProviderWithExpectedEndpointAndModel() {
        assertEquals(AiProvider.GROQ, AiProvider.fromStoredName(null))
        assertEquals(AiProvider.GROQ, AiProvider.fromStoredName(""))
        assertEquals(AiProvider.GROQ, AiProvider.fromStoredName("UNKNOWN"))
        assertEquals(AiProvider.GROQ, AiProvider.fromStoredName("groq"))
        assertEquals(AiProvider.GEMINI, AiProvider.fromStoredName("GEMINI"))

        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            AiCoachService.GROQ_ENDPOINT
        )
        assertEquals("llama-3.3-70b-versatile", AiCoachService.GROQ_MODEL_NAME)
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
        assertEquals(
            listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
            AiCoachService.GROQ_MODEL_LIST
        )
        assertEquals(
            "https://api.groq.com/openai/v1/models",
            AiCoachService.GROQ_MODELS_ENDPOINT
        )
    }

    @Test
    fun selectFirstActiveGroqChatModelSkipsWhisperTtsGuardAudioAndTriedModels() {
        val modelsJson = """
            {
              "data": [
                { "id": "whisper-large-v3", "active": true },
                { "id": "playai-tts", "active": true },
                { "id": "llama-guard-3-8b", "active": true },
                { "id": "distil-whisper-audio-en", "active": true },
                { "id": "llama-3.3-70b-versatile", "active": true },
                { "id": "inactive-chat-model", "active": false },
                { "id": "qwen-qwq-32b", "active": true }
              ]
            }
        """.trimIndent()

        val selected = AiCoachService.selectFirstActiveGroqChatModel(
            modelsResponseJson = modelsJson,
            excludeModels = setOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant")
        )
        assertEquals("qwen-qwq-32b", selected)
    }

    @Test
    fun groq404FallsBackToInstantThenDiscoversActiveChatModel() = runBlocking {
        val calledModels = mutableListOf<String>()
        var discoveryCalled = false

        val result = AiCoachService.executeGroqWithFallbackAndDiscovery(
            models = AiCoachService.GROQ_MODEL_LIST,
            delayFn = {},
            discoverChatModelFn = { tried ->
                discoveryCalled = true
                assertTrue("llama-3.3-70b-versatile" in tried)
                assertTrue("llama-3.1-8b-instant" in tried)
                "llama3-70b-8192"
            },
            invoker = { model ->
                calledModels.add(model)
                if (model == "llama3-70b-8192") {
                    AiCoachService.GroqAttemptResult.Success("{\"ok\":true}")
                } else {
                    AiCoachService.GroqAttemptResult.ModelNotFoundFailure("Groq AI (404): model not found")
                }
            }
        )

        assertTrue(discoveryCalled)
        assertEquals(
            listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "llama3-70b-8192"),
            calledModels
        )
        assertTrue(result is GeminiCoachService.GeminiHttpResult.Success)
        assertEquals("llama3-70b-8192", (result as GeminiCoachService.GeminiHttpResult.Success).modelUsed)
    }

    @Test
    fun extractServerErrorMessageReadsGroqAndGeminiErrorJson() {
        val groqErr = """{"error":{"message":"The model `old-model` does not exist","type":"invalid_request_error"}}"""
        assertEquals(
            "The model `old-model` does not exist",
            AiCoachService.extractServerErrorMessage(groqErr)
        )
        assertEquals(null, AiCoachService.extractServerErrorMessage("not json"))
    }
}
