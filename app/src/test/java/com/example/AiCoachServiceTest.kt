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
        assertEquals("llama-3.3-70b-versatile", AiProvider.GROQ.modelSummary)
        assertEquals("gemini-3.8-flash", AiProvider.GEMINI.modelSummary)
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
    fun sanitizeGroqModelBlocksEmptyOrpheusAndUnknownModels() {
        assertEquals("llama-3.3-70b-versatile", AiCoachService.sanitizeGroqModel(null))
        assertEquals("llama-3.3-70b-versatile", AiCoachService.sanitizeGroqModel(""))
        assertEquals("llama-3.3-70b-versatile", AiCoachService.sanitizeGroqModel("   "))
        assertEquals(
            "llama-3.3-70b-versatile",
            AiCoachService.sanitizeGroqModel("canopylabs/orpheus-arabic-saudi")
        )
        assertEquals(
            "llama-3.3-70b-versatile",
            AiCoachService.sanitizeGroqModel("ORPHEUS-v1-english")
        )
        assertEquals(
            "llama-3.3-70b-versatile",
            AiCoachService.sanitizeGroqModel("whisper-large-v3")
        )
        assertEquals(
            "llama-3.3-70b-versatile",
            AiCoachService.sanitizeGroqModel("llama-3.3-70b-versatile")
        )
        assertEquals(
            "llama-3.1-8b-instant",
            AiCoachService.sanitizeGroqModel("llama-3.1-8b-instant")
        )
    }

    @Test
    fun buildGroqRequestJsonHardcodesAllowedLlamaModelsOnly() {
        val primaryBody = AiCoachService.buildGroqRequestJson(
            model = "canopylabs/orpheus-arabic-saudi",
            systemPrompt = "System prompt",
            userPrompt = "User prompt",
            jsonMode = true
        )
        assertTrue(primaryBody.contains("\"model\":\"llama-3.3-70b-versatile\""))
        assertTrue(primaryBody.contains("\"response_format\""))

        val fallbackBody = AiCoachService.buildGroqRequestJson(
            model = "llama-3.1-8b-instant",
            systemPrompt = "System prompt",
            userPrompt = "User prompt",
            jsonMode = false
        )
        assertTrue(fallbackBody.contains("\"model\":\"llama-3.1-8b-instant\""))
    }

    @Test
    fun groq404FallsBackFrom70bTo8bInstantWithoutCallingModelsEndpoint() = runBlocking {
        val calledModels = mutableListOf<String>()

        val result = AiCoachService.executeGroqWithFallback(
            models = AiCoachService.GROQ_MODEL_LIST,
            delayFn = {},
            invoker = { model ->
                calledModels.add(model)
                if (model == "llama-3.3-70b-versatile") {
                    AiCoachService.GroqAttemptResult.ModelNotFoundFailure(
                        AiCoachService.formatHttpDiagnostic(
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

        assertEquals(
            listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
            calledModels
        )
        assertTrue(result is GeminiCoachService.GeminiHttpResult.Success)
        assertEquals(
            "llama-3.1-8b-instant",
            (result as GeminiCoachService.GeminiHttpResult.Success).modelUsed
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
        assertTrue(diagnostic.contains("[Model terkirim: llama-3.3-70b-versatile]"))
        assertTrue(diagnostic.contains("The model `foo` does not exist"))

        val result = AiCoachService.executeGroqWithFallback(
            models = listOf("canopylabs/orpheus-arabic-saudi"),
            delayFn = {},
            invoker = { model ->
                AiCoachService.GroqAttemptResult.NonRetryableFailure(
                    AiCoachService.formatHttpDiagnostic(
                        statusCode = 400,
                        endpointUrl = AiCoachService.GROQ_ENDPOINT,
                        sentModel = model,
                        serverErrorMessage = "Invalid API key"
                    )
                )
            }
        )

        assertTrue(result is GeminiCoachService.GeminiHttpResult.Failure)
        val message = (result as GeminiCoachService.GeminiHttpResult.Failure).userMessage
        assertTrue(message.contains("HTTP 400"))
        assertTrue(message.contains("api.groq.com/openai/v1/chat/completions"))
        assertTrue(message.contains("[Model terkirim: llama-3.3-70b-versatile]"))
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
