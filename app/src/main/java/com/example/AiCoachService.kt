package com.example

import com.example.domain.CoachingEngine
import com.example.model.DailyRecommendation
import com.example.model.TodayStatus
import com.example.model.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

enum class AiProvider(
    val displayName: String,
    val badgeLabel: String,
    val modelSummary: String
) {
    GROQ(
        displayName = "Groq",
        badgeLabel = "Groq AI",
        modelSummary = "llama-3.3-70b-versatile"
    ),
    GEMINI(
        displayName = "Gemini",
        badgeLabel = "Gemini AI",
        modelSummary = "gemini-3.8-flash"
    );

    companion object {
        fun fromStoredName(raw: String?): AiProvider {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: GROQ
        }
    }
}

/**
 * Layanan Multi-Provider AI untuk Anti-Mager Coach:
 * - Groq (Default): endpoint OpenAI-compatible Chat Completions dengan model llama-3.3-70b-versatile
 * - Gemini (Cadangan): delegasi ke [GeminiCoachService] dengan auto-retry & fallback model Flash
 */
object AiCoachService {

    const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    const val GROQ_MODEL_NAME = "llama-3.3-70b-versatile"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 35_000

    suspend fun generateDailySuggestions(
        provider: AiProvider = AiProvider.GROQ,
        apiKey: String,
        sessions: List<WorkoutSession>,
        energyState: TodayStatus,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyRecommendation? {
        return when (
            val result = generateDailySuggestionsDetailed(
                provider = provider,
                apiKey = apiKey,
                sessions = sessions,
                energyState = energyState,
                nowMillis = nowMillis
            )
        ) {
            is DailySuggestionsResult.Success -> result.recommendation
            is DailySuggestionsResult.FallbackToLocal -> null
        }
    }

    suspend fun generateDailySuggestionsDetailed(
        provider: AiProvider = AiProvider.GROQ,
        apiKey: String,
        sessions: List<WorkoutSession>,
        energyState: TodayStatus,
        nowMillis: Long = System.currentTimeMillis()
    ): DailySuggestionsResult {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return DailySuggestionsResult.FallbackToLocal(
                friendlyMessage = "Isi API Key ${provider.displayName} di Pengaturan terlebih dahulu."
            )
        }

        return when (provider) {
            AiProvider.GEMINI -> GeminiCoachService.generateDailySuggestionsDetailed(
                apiKey = cleanKey,
                sessions = sessions,
                energyState = energyState,
                nowMillis = nowMillis
            )
            AiProvider.GROQ -> generateGroqDailySuggestions(
                apiKey = cleanKey,
                sessions = sessions,
                energyState = energyState,
                nowMillis = nowMillis
            )
        }
    }

    suspend fun generateWeeklyReview(
        provider: AiProvider = AiProvider.GROQ,
        apiKey: String,
        sessions: List<WorkoutSession>,
        nowMillis: Long = System.currentTimeMillis()
    ): WeeklyReviewResult {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return WeeklyReviewResult.Error(
                "API Key ${provider.displayName} belum diisi. Silakan masukkan API Key di Pengaturan terlebih dahulu."
            )
        }

        return when (provider) {
            AiProvider.GEMINI -> GeminiCoachService.generateWeeklyReview(
                apiKey = cleanKey,
                sessions = sessions,
                nowMillis = nowMillis
            )
            AiProvider.GROQ -> generateGroqWeeklyReview(
                apiKey = cleanKey,
                sessions = sessions,
                nowMillis = nowMillis
            )
        }
    }

    internal fun buildDailyUserPrompt(
        sessions: List<WorkoutSession>,
        energyState: TodayStatus,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val history14Days = GeminiCoachService.summarizeSessions(
            sessions = sessions,
            daysWindow = 14,
            nowMillis = nowMillis
        )
        return """
            Kondisi user hari ini: ${energyState.label} (${energyState.shortHint})
            Ringkasan riwayat latihan 14 hari terakhir:
            $history14Days

            Tugasmu:
            Buat 2 pilihan latihan hari ini:
            1. "ideal": pilihan utama yang paling bijak melihat riwayat (perhatikan kelelahan kaki/otot 48 jam terakhir, hari beruntun aktif, dan keseimbangan kardio vs kekuatan).
            2. "light": pilihan versi super ringan anti-mager agar user tetap menang tanpa rasa bersalah.

            Aturan penting:
            - Sebutkan secara spesifik konteks dari riwayat user (atau sambut hangat bila riwayat masih kosong) di bagian "reason".
            - "sportType" WAJIB salah satu dari: RUNNING, BADMINTON, WALKING, DUMBBELL, REST.
            - "durationMin" berupa angka bulat menit yang realistis (antara 8 sampai 60).
            - "insightBadge" maksimal 65 karakter dengan 1 emoji di depan.
            - "guidance" berupa panduan santai 1-2 kalimat tanpa tekanan pace/target berat.

            Balas HANYA dalam JSON valid sesuai skema persis ini tanpa teks tambahan:
            {
              "insightBadge": "...",
              "ideal": { "title": "...", "sportType": "RUNNING|BADMINTON|WALKING|DUMBBELL|REST", "durationMin": 25, "reason": "...", "guidance": "..." },
              "light": { "title": "...", "sportType": "RUNNING|BADMINTON|WALKING|DUMBBELL|REST", "durationMin": 12, "reason": "...", "guidance": "..." }
            }
        """.trimIndent()
    }

    internal fun buildWeeklyUserPrompt(
        sessions: List<WorkoutSession>,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val summary7Days = GeminiCoachService.summarizeSessions(
            sessions = sessions,
            daysWindow = 7,
            nowMillis = nowMillis
        )
        val rhythm = CoachingEngine.computeWeeklyRhythm(sessions, nowMillis)
        return """
            Statistik 7 hari terakhir user:
            - Hari aktif: ${rhythm.activeDaysCount}/7 hari (${rhythm.consistencyPercent}%)
            - Total durasi aktif: ${rhythm.totalActiveMinutes} menit
            - Jumlah sesi kekuatan/beban: ${rhythm.strengthSessionCount}
            - Jumlah sesi kardio: ${rhythm.cardioSessionCount}

            Detail sesi 7 hari terakhir:
            $summary7Days

            Buat evaluasi mingguan singkat bergaya teman dalam format teks rapi MAKSIMAL 3 poin pendek (tiap poin 1-2 kalimat, masing-masing diawali emoji yang sesuai, tanpa tabel atau markdown rumit):
            1. Apresiasi minggu ini (WAJIB sebutkan angka atau jenis latihan asli dari data di atas; jika masih 0 sesi, beri semangat ramah untuk mulai pelan).
            2. Keseimbangan tubuh (komentar santai apakah porsi kardio, beban/bodyweight, dan istirahat sudah pas).
            3. 1 Fokus ringan untuk minggu depan yang realistis dan bebas tekanan.
        """.trimIndent()
    }

    private suspend fun generateGroqDailySuggestions(
        apiKey: String,
        sessions: List<WorkoutSession>,
        energyState: TodayStatus,
        nowMillis: Long
    ): DailySuggestionsResult = withContext(Dispatchers.IO) {
        val systemPrompt =
            "Kamu adalah \"Anti-Mager Coach\", pelatih kebugaran pribadi yang santai, suportif, anti-stres, berbahasa Indonesia natural, dan paham keseimbangan kardio vs kekuatan serta pemulihan otot. Selalu balas hanya dalam objek JSON valid sesuai skema yang diminta."
        val userPrompt = buildDailyUserPrompt(
            sessions = sessions,
            energyState = energyState,
            nowMillis = nowMillis
        )
        val insights = CoachingEngine.analyzeInsights(sessions, nowMillis)

        when (
            val apiCall = callGroqWithRetry(
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                jsonMode = true
            )
        ) {
            is GeminiCoachService.GeminiHttpResult.Success -> {
                val parsed = GeminiCoachService.parseDailySuggestionJson(
                    rawJson = apiCall.text,
                    energyState = energyState,
                    sessions = sessions,
                    nowMillis = nowMillis,
                    insights = insights,
                    providerLabel = AiProvider.GROQ.badgeLabel
                )
                if (parsed != null) {
                    DailySuggestionsResult.Success(parsed)
                } else {
                    DailySuggestionsResult.FallbackToLocal(
                        friendlyMessage = "Format respons Groq AI kurang sesuai — menampilkan saran aturan lokal."
                    )
                }
            }
            is GeminiCoachService.GeminiHttpResult.Failure -> {
                DailySuggestionsResult.FallbackToLocal(
                    friendlyMessage = apiCall.userMessage,
                    isOverloaded = apiCall.isOverloaded
                )
            }
        }
    }

    private suspend fun generateGroqWeeklyReview(
        apiKey: String,
        sessions: List<WorkoutSession>,
        nowMillis: Long
    ): WeeklyReviewResult = withContext(Dispatchers.IO) {
        val systemPrompt =
            "Kamu adalah \"Anti-Mager Coach\", teman olahraga yang hangat, suportif, santai, anti-stres, berbahasa Indonesia natural. Balas rapi maksimal 3 poin pendek dengan emoji."
        val userPrompt = buildWeeklyUserPrompt(
            sessions = sessions,
            nowMillis = nowMillis
        )

        when (
            val apiCall = callGroqWithRetry(
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                jsonMode = false
            )
        ) {
            is GeminiCoachService.GeminiHttpResult.Success -> {
                val cleaned = apiCall.text.trim()
                if (cleaned.isEmpty()) {
                    WeeklyReviewResult.Error("Groq AI tidak mengembalikan teks evaluasi. Coba lagi sebentar lagi.")
                } else {
                    WeeklyReviewResult.Success(cleaned)
                }
            }
            is GeminiCoachService.GeminiHttpResult.Failure -> {
                WeeklyReviewResult.Error(apiCall.userMessage)
            }
        }
    }

    private suspend fun callGroqWithRetry(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        jsonMode: Boolean
    ): GeminiCoachService.GeminiHttpResult {
        return GeminiCoachService.executeWithRetryAndFallback(
            models = listOf(GROQ_MODEL_NAME)
        ) { model ->
            callGroqSingleAttempt(
                model = model,
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                jsonMode = jsonMode
            )
        }
    }

    private fun callGroqSingleAttempt(
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        jsonMode: Boolean
    ): GeminiCoachService.SingleAttemptResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(GROQ_ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val requestBody = buildGroqRequestJson(
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                jsonMode = jsonMode
            )
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            when (val statusCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val rawBody = GeminiCoachService.readBoundedStream(connection.inputStream)
                    val extracted = extractGroqContent(rawBody)
                    if (extracted.isNullOrBlank()) {
                        GeminiCoachService.SingleAttemptResult.NonRetryableFailure(
                            "Respons Groq AI kosong. Coba beberapa saat lagi."
                        )
                    } else {
                        GeminiCoachService.SingleAttemptResult.Success(extracted)
                    }
                }
                HttpURLConnection.HTTP_UNAVAILABLE -> {
                    GeminiCoachService.SingleAttemptResult.RetryableFailure(
                        statusCode = HttpURLConnection.HTTP_UNAVAILABLE,
                        userMessage = GeminiCoachService.OVERLOADED_FRIENDLY_MESSAGE
                    )
                }
                429 -> {
                    GeminiCoachService.SingleAttemptResult.RetryableFailure(
                        statusCode = 429,
                        userMessage = "Kuota Groq AI sementara penuh. Coba tekan Refresh sebentar lagi ya."
                    )
                }
                HttpURLConnection.HTTP_BAD_REQUEST,
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    GeminiCoachService.SingleAttemptResult.NonRetryableFailure(
                        "API Key Groq tidak valid atau belum aktif. Periksa kembali di Pengaturan."
                    )
                }
                else -> {
                    GeminiCoachService.SingleAttemptResult.NonRetryableFailure(
                        "Layanan Groq AI sedang tidak merespons (kode $statusCode). Coba lagi nanti."
                    )
                }
            }
        } catch (_: Exception) {
            GeminiCoachService.SingleAttemptResult.NonRetryableFailure(
                "Tidak dapat terhubung ke Groq AI. Periksa koneksi internetmu."
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildGroqRequestJson(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        jsonMode: Boolean
    ): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userPrompt))

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.7)

        if (jsonMode) {
            payload.put("response_format", JSONObject().put("type", "json_object"))
        }

        return payload.toString()
    }

    private fun extractGroqContent(rawResponse: String): String? {
        return try {
            val root = JSONObject(rawResponse)
            val choices = root.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val message = choices.optJSONObject(0)?.optJSONObject("message") ?: return null
            if (message.isNull("content")) return null
            message.optString("content", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
