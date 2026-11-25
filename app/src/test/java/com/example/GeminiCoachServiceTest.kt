package com.example

import com.example.domain.CoachingEngine
import com.example.domain.DateTimeHelper
import com.example.model.DumbbellItem
import com.example.model.SessionSource
import com.example.model.SportType
import com.example.model.TodayStatus
import com.example.model.WorkoutSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiCoachServiceTest {

    @Test
    fun modelListContainsPrimaryFlashAndStableLiteFallback() {
        assertEquals(listOf("gemini-3.8-flash", "gemini-3.5-flash-lite"), GeminiCoachService.MODEL_LIST)
        assertEquals("gemini-3.8-flash", GeminiCoachService.MODEL_NAME)
        assertEquals(2, GeminiCoachService.MAX_RETRY_ATTEMPTS)
        assertEquals(1_500L, GeminiCoachService.RETRY_DELAY_MS)
    }

    @Test
    fun retrySucceedsOnThirdAttemptAfterTwo503Responses() = runBlocking {
        val calledModels = mutableListOf<String>()
        val delays = mutableListOf<Long>()
        var attempts = 0

        val result = GeminiCoachService.executeWithRetryAndFallback(
            delayFn = { ms -> delays.add(ms) }
        ) { model ->
            calledModels.add(model)
            attempts++
            if (attempts <= 2) {
                GeminiCoachService.SingleAttemptResult.RetryableFailure(
                    statusCode = 503,
                    userMessage = GeminiCoachService.OVERLOADED_FRIENDLY_MESSAGE
                )
            } else {
                GeminiCoachService.SingleAttemptResult.Success("OK_PRIMARY")
            }
        }

        assertTrue(result is GeminiCoachService.GeminiHttpResult.Success)
        val success = result as GeminiCoachService.GeminiHttpResult.Success
        assertEquals("OK_PRIMARY", success.text)
        assertEquals("gemini-3.8-flash", success.modelUsed)
        assertEquals(listOf("gemini-3.8-flash", "gemini-3.8-flash", "gemini-3.8-flash"), calledModels)
        assertEquals(listOf(1_500L, 1_500L), delays)
    }

    @Test
    fun retryHandles429With1500msDelay() = runBlocking {
        val delays = mutableListOf<Long>()
        var attempts = 0

        val result = GeminiCoachService.executeWithRetryAndFallback(
            delayFn = { ms -> delays.add(ms) }
        ) { _ ->
            attempts++
            if (attempts == 1) {
                GeminiCoachService.SingleAttemptResult.RetryableFailure(
                    statusCode = 429,
                    userMessage = "Rate limit"
                )
            } else {
                GeminiCoachService.SingleAttemptResult.Success("RECOVERED_429")
            }
        }

        assertTrue(result is GeminiCoachService.GeminiHttpResult.Success)
        assertEquals("RECOVERED_429", (result as GeminiCoachService.GeminiHttpResult.Success).text)
        assertEquals(listOf(1_500L), delays)
    }

    @Test
    fun fallsBackToSecondaryModelWhenPrimaryRemains503AfterRetries() = runBlocking {
        val calledModels = mutableListOf<String>()
        val delays = mutableListOf<Long>()

        val result = GeminiCoachService.executeWithRetryAndFallback(
            delayFn = { ms -> delays.add(ms) }
        ) { model ->
            calledModels.add(model)
            if (model == GeminiCoachService.MODEL_LIST[0]) {
                GeminiCoachService.SingleAttemptResult.RetryableFailure(
                    statusCode = 503,
                    userMessage = GeminiCoachService.OVERLOADED_FRIENDLY_MESSAGE
                )
            } else {
                GeminiCoachService.SingleAttemptResult.Success("OK_FALLBACK_LITE")
            }
        }

        assertTrue(result is GeminiCoachService.GeminiHttpResult.Success)
        val success = result as GeminiCoachService.GeminiHttpResult.Success
        assertEquals("OK_FALLBACK_LITE", success.text)
        assertEquals("gemini-3.5-flash-lite", success.modelUsed)
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.8-flash", "gemini-3.8-flash", "gemini-3.5-flash-lite"),
            calledModels
        )
        assertEquals(listOf(1_500L, 1_500L), delays)
    }

    @Test
    fun returnsFriendlyMessageWhenAllModelsFailWith503() = runBlocking {
        val calledModels = mutableListOf<String>()
        val delays = mutableListOf<Long>()

        val result = GeminiCoachService.executeWithRetryAndFallback(
            delayFn = { ms -> delays.add(ms) }
        ) { model ->
            calledModels.add(model)
            GeminiCoachService.SingleAttemptResult.RetryableFailure(
                statusCode = 503,
                userMessage = GeminiCoachService.OVERLOADED_FRIENDLY_MESSAGE
            )
        }

        assertTrue(result is GeminiCoachService.GeminiHttpResult.Failure)
        val failure = result as GeminiCoachService.GeminiHttpResult.Failure
        assertTrue(failure.isOverloaded)
        assertEquals(
            "Server AI lagi padat sebentar, coba tekan Refresh lagi ya",
            failure.userMessage
        )
        assertEquals(6, calledModels.size) // 3 attempts per model x 2 models
        assertEquals(4, delays.size) // 2 retry delays per model x 2 models
    }

    @Test
    fun nonRetryableAuthFailureStopsImmediatelyWithoutFallback() = runBlocking {
        val calledModels = mutableListOf<String>()
        val delays = mutableListOf<Long>()

        val result = GeminiCoachService.executeWithRetryAndFallback(
            delayFn = { ms -> delays.add(ms) }
        ) { model ->
            calledModels.add(model)
            GeminiCoachService.SingleAttemptResult.NonRetryableFailure("API Key tidak valid")
        }

        assertTrue(result is GeminiCoachService.GeminiHttpResult.Failure)
        val failure = result as GeminiCoachService.GeminiHttpResult.Failure
        assertFalse(failure.isOverloaded)
        assertEquals("API Key tidak valid", failure.userMessage)
        assertEquals(listOf("gemini-3.8-flash"), calledModels)
        assertTrue(delays.isEmpty())
    }

    @Test
    fun summarizeSessionsIncludesOnlySessionsWithinWindow() {
        val nowMillis = 1_750_000_000_000L
        val oneDayMs = 86_400_000L

        val recentRun = WorkoutSession(
            id = "s1",
            startedAtIso = DateTimeHelper.formatNowIso(nowMillis - oneDayMs),
            sportType = SportType.RUNNING,
            durationMin = 30,
            distanceKm = 4.5,
            rpe = 6,
            notes = "Lari santai pagi",
            source = SessionSource.MANUAL
        )
        val recentDumbbell = WorkoutSession(
            id = "s2",
            startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 3 * oneDayMs),
            sportType = SportType.DUMBBELL,
            durationMin = 25,
            rpe = 5,
            dumbbellItems = listOf(DumbbellItem("Goblet Squat", 5.0, 3, 10)),
            source = SessionSource.MANUAL
        )
        val oldSession = WorkoutSession(
            id = "s3",
            startedAtIso = DateTimeHelper.formatNowIso(nowMillis - 20 * oneDayMs),
            sportType = SportType.BADMINTON,
            durationMin = 60,
            rpe = 8,
            notes = "Sesi lama di luar 14 hari",
            source = SessionSource.MANUAL
        )

        val summary14 = GeminiCoachService.summarizeSessions(
            sessions = listOf(recentRun, recentDumbbell, oldSession),
            daysWindow = 14,
            nowMillis = nowMillis
        )
        assertTrue(summary14.contains("Lari"))
        assertTrue(summary14.contains("30 menit"))
        assertTrue(summary14.contains("RPE 6/10"))
        assertTrue(summary14.contains("4.5 km"))
        assertTrue(summary14.contains("Goblet Squat"))
        assertTrue(summary14.contains("Lari santai pagi"))
        assertTrue(!summary14.contains("Sesi lama di luar 14 hari"))

        val emptySummary = GeminiCoachService.summarizeSessions(
            sessions = listOf(oldSession),
            daysWindow = 7,
            nowMillis = nowMillis
        )
        assertTrue(emptySummary.contains("Belum ada sesi latihan tercatat dalam 7 hari terakhir"))
    }

    @Test
    fun parseSportTypeMapsCanonicalAndIndonesianVariants() {
        assertEquals(SportType.RUNNING, GeminiCoachService.parseSportType("RUNNING"))
        assertEquals(SportType.RUNNING, GeminiCoachService.parseSportType("lari"))
        assertEquals(SportType.BADMINTON, GeminiCoachService.parseSportType("BADMINTON"))
        assertEquals(SportType.DUMBBELL, GeminiCoachService.parseSportType("DUMBBELL"))
        assertEquals(SportType.DUMBBELL, GeminiCoachService.parseSportType("strength"))
        assertEquals(SportType.REST, GeminiCoachService.parseSportType("REST"))
        assertEquals(SportType.WALKING, GeminiCoachService.parseSportType("UNKNOWN_SPORT"))
    }

    @Test
    fun stripCodeFenceIfPresentRemovesMarkdownJsonFences() {
        val fenced = "```json\n{\"insightBadge\":\"✨ Mantap\"}\n```"
        assertEquals("{\"insightBadge\":\"✨ Mantap\"}", GeminiCoachService.stripCodeFenceIfPresent(fenced))

        val plain = "{\"insightBadge\":\"✨ Mantap\"}"
        assertEquals(plain, GeminiCoachService.stripCodeFenceIfPresent(plain))
    }

    @Test
    fun buildDailyRecommendationFromParsedMarksAiGeneratedAndClampsDurations() {
        val insights = CoachingEngine.analyzeInsights(emptyList())
        val rec = GeminiCoachService.buildDailyRecommendationFromParsed(
            insightBadge = "✨ Ritme seimbang minggu ini",
            ideal = GeminiCoachService.ParsedAiOption(
                title = "Latihan Upper Body Dumbbell",
                sportType = "DUMBBELL",
                durationMin = 120,
                reason = "Kemarin sudah lari 30 menit, hari ini giliran otot atas.",
                guidance = "Ambil napas panjang di antara setiap set."
            ),
            light = GeminiCoachService.ParsedAiOption(
                title = "Jalan Santai Sore",
                sportType = "WALKING",
                durationMin = 2,
                reason = "Biar tetap gerak tanpa lelah.",
                guidance = "Cukup keliling blok rumah santai."
            ),
            energyState = TodayStatus.FRESH,
            insights = insights
        )

        assertNotNull(rec)
        val nonNullRec = rec!!
        assertTrue(nonNullRec.isAiGenerated)
        assertEquals("✨ Ritme seimbang minggu ini", nonNullRec.insightBadge)
        assertEquals(SportType.DUMBBELL, nonNullRec.idealOption.sportType)
        assertEquals(90, nonNullRec.idealOption.estimatedDurationMin)
        assertTrue(nonNullRec.idealOption.suggestedDumbbellItems.isNotEmpty())
        assertEquals(SportType.WALKING, nonNullRec.lightOption.sportType)
        assertEquals(5, nonNullRec.lightOption.estimatedDurationMin)
    }

    @Test
    fun buildDailyRecommendationReturnsNullWhenTitleIsBlank() {
        val insights = CoachingEngine.analyzeInsights(emptyList())
        val rec = GeminiCoachService.buildDailyRecommendationFromParsed(
            insightBadge = "Badge",
            ideal = GeminiCoachService.ParsedAiOption(
                title = "   ",
                sportType = "WALKING",
                durationMin = 15,
                reason = "Reason",
                guidance = "Guidance"
            ),
            light = GeminiCoachService.ParsedAiOption(
                title = "Light",
                sportType = "REST",
                durationMin = 10,
                reason = "Reason",
                guidance = "Guidance"
            ),
            energyState = TodayStatus.TIRED,
            insights = insights
        )
        assertNull(rec)
    }

    @Test
    fun sanitizeGeminiModelPreservesGeminiAndRejectsGroqLlamaModel() {
        assertEquals("gemini-3.8-flash", GeminiCoachService.sanitizeGeminiModel("gemini-3.8-flash"))
        assertEquals("gemini-3.5-flash-lite", GeminiCoachService.sanitizeGeminiModel("gemini-3.5-flash-lite"))
        assertEquals("gemini-3.8-flash", GeminiCoachService.sanitizeGeminiModel("llama-3.3-70b-versatile"))
        assertEquals("gemini-3.8-flash", GeminiCoachService.sanitizeGeminiModel("openai/gpt-oss-20b"))
        assertEquals("gemini-3.8-flash", GeminiCoachService.sanitizeGeminiModel("canopylabs/orpheus-arabic-saudi"))
        assertEquals("gemini-3.8-flash", GeminiCoachService.sanitizeGeminiModel(""))
        assertEquals("gemini-3.8-flash", GeminiCoachService.sanitizeGeminiModel(null))
        assertFalse(GeminiCoachService.MODEL_LIST.contains("llama-3.3-70b-versatile"))
    }
}
