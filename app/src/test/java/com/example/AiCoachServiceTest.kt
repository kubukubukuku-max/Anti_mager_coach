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
}
