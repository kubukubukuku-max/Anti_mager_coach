package com.example

import com.example.domain.CoachingEngine
import com.example.model.DailyRecommendation
import com.example.model.TodayStatus
import com.example.model.WorkoutSession
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
 * - Groq (Default): POST langsung ke https://api.groq.com/openai/v1/chat/completions
 *   dengan model utama "llama-3.3-70b-versatile" dan cadangan "llama-3.1-8b-instant".
 *   TIDAK pernah memanggil /models dan TIDAK pernah membaca nama model dari SharedPreferences.
 * - Auto-routing: API Key yang diawali "gsk_" wajib diarahkan ke endpoint Groq.
 * - Gemini (Cadangan): delegasi ke [GeminiCoachService] dengan model Gemini Flash terpisah.
 */
object AiCoachService {

    const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    const val GROQ_MODEL_NAME = "llama-3.3-70b-versatile"
    const val GROQ_FALLBACK_MODEL_NAME = "llama-3.1-8b-instant"

    val GROQ_MODEL_LIST: List<String> = listOf(
        GROQ_MODEL_NAME,
        GROQ_FALLBACK_MODEL_NAME
    )

    val ALLOWED_GROQ_MODELS: Set<String> = GROQ_MODEL_LIST.toSet()

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 35_000

    private val moshiMapAdapter = Moshi.Builder().build().adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    /**
     * Pengaman ketat model Groq: hanya mengizinkan "llama-3.3-70b-versatile" atau
     * "llama-3.1-8b-instant". Nilai lain (kosong, orpheus, nama model Gemini, dll.)
     * langsung diganti paksa ke "llama-3.3-70b-versatile".
     */
    fun sanitizeGroqModel(rawModel: String?): String {
        val candidate = rawModel?.trim().orEmpty()
        if (candidate.isEmpty()) return GROQ_MODEL_NAME
        if (candidate.contains("orpheus", ignoreCase = true)) return GROQ_MODEL_NAME
        return if (candidate in ALLOWED_GROQ_MODELS) candidate else GROQ_MODEL_NAME
    }

    /**
     * Memastikan API Key yang diawali `gsk_` selalu diarahkan ke endpoint Groq,
     * mengabaikan pilihan provider Gemini yang mungkin masih tersisa di penyimpanan lokal.
     */
    fun resolveEffectiveProvider(requestedProvider: AiProvider, apiKey: String): AiProvider {
        val cleanKey = apiKey.trim()
        return if (cleanKey.startsWith("gsk_")) {
            AiProvider.GROQ
        } else {
            requestedProvider
        }
    }

    internal fun appendSentModelTag(message: String, sentModel: String): String {
        val tag = "[Model terkirim: $sentModel]"
        val cleaned = message.trim()
        return if (cleaned.contains("[Model terkirim:")) {
            cleaned
        } else {
            "$cleaned $tag"
        }
    }

    /**
     * Format pesan diagnostik singkat untuk respons HTTP non-200 agar selalu memuat:
     * kode HTTP, domain/path tujuan (tanpa query/key), nama model yang dikirim, dan error.message server.
     */
    internal fun formatHttpDiagnostic(
        statusCode: Int,
        endpointUrl: String,
        sentModel: String,
        serverErrorMessage: String?
    ): String {
        val target = endpointUrl
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('?')
        val detail = serverErrorMessage?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Tidak ada pesan detail dari server"
        return "HTTP $statusCode | $target | [Model terkirim: $sentModel] | $detail"
    }

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
        val effectiveProvider = resolveEffectiveProvider(provider, cleanKey)
        if (cleanKey.isEmpty()) {
            return DailySuggestionsResult.FallbackToLocal(
                friendlyMessage = "Isi API Key ${effectiveProvider.displayName} di Pengaturan terlebih dahulu."
            )
        }

        return when (effectiveProvider) {
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
        val effectiveProvider = resolveEffectiveProvider(provider, cleanKey)
        if (cleanKey.isEmpty()) {
            return WeeklyReviewResult.Error(
                "API Key ${effectiveProvider.displayName} belum diisi. Silakan masukkan API Key di Pengaturan terlebih dahulu."
            )
        }

        return when (effectiveProvider) {
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

    /**
     * Mengekstrak `error.message` (atau `message`) secara ringkas dari respons error JSON server.
     * Jika respons bukan JSON valid, tetap mengambil potongan teks singkat agar diagnostik jelas.
     */
    internal fun extractServerErrorMessage(rawErrorBody: String?): String? {
        if (rawErrorBody.isNullOrBlank()) return null
        val trimmed = rawErrorBody.trim()
        return try {
            val root = moshiMapAdapter.fromJson(trimmed) ?: return fallbackPlainErrorSnippet(trimmed)
            val errorObj = root["error"] as? Map<*, *>
            val nestedMessage = (errorObj?.get("message") as? String)?.trim()
            val topMessage = (root["message"] as? String)?.trim()
            val chosen = when {
                !nestedMessage.isNullOrEmpty() -> nestedMessage
                !topMessage.isNullOrEmpty() -> topMessage
                else -> null
            }
            chosen?.replace(Regex("\\s+"), " ")?.take(160)
        } catch (_: Exception) {
            fallbackPlainErrorSnippet(trimmed)
        }
    }

    private fun fallbackPlainErrorSnippet(raw: String): String? {
        val stripped = raw
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return stripped.takeIf { it.isNotEmpty() }?.take(120)
    }

    internal sealed class GroqAttemptResult {
        data class Success(val text: String) : GroqAttemptResult()
        data class RetryableFailure(val statusCode: Int, val userMessage: String) : GroqAttemptResult()
        data class ModelNotFoundFailure(val userMessage: String) : GroqAttemptResult()
        data class NonRetryableFailure(val userMessage: String) : GroqAttemptResult()
    }

    /**
     * Orkestrasi panggilan Groq AI hanya pada daftar model tetap:
     * 1. "llama-3.3-70b-versatile" (utama)
     * 2. "llama-3.1-8b-instant" (cadangan saat 404 / 503 / 429)
     * Tidak pernah memanggil /models dan tidak pernah membaca model dari SharedPreferences.
     */
    internal suspend fun executeGroqWithFallback(
        models: List<String> = GROQ_MODEL_LIST,
        maxRetries: Int = GeminiCoachService.MAX_RETRY_ATTEMPTS,
        retryDelayMs: Long = GeminiCoachService.RETRY_DELAY_MS,
        delayFn: suspend (Long) -> Unit = { delay(it) },
        invoker: suspend (model: String) -> GroqAttemptResult
    ): GeminiCoachService.GeminiHttpResult {
        val safeModels = models
            .map { sanitizeGroqModel(it) }
            .distinct()
            .ifEmpty { GROQ_MODEL_LIST }

        var sawServiceUnavailable = false
        var lastErrorMessage = appendSentModelTag(
            GeminiCoachService.OVERLOADED_FRIENDLY_MESSAGE,
            safeModels.first()
        )

        for (model in safeModels) {
            var moveToNextModel = false
            for (attempt in 0..maxRetries) {
                when (val res = invoker(model)) {
                    is GroqAttemptResult.Success -> {
                        return GeminiCoachService.GeminiHttpResult.Success(
                            text = res.text,
                            modelUsed = model
                        )
                    }
                    is GroqAttemptResult.RetryableFailure -> {
                        if (res.statusCode == HttpURLConnection.HTTP_UNAVAILABLE) {
                            sawServiceUnavailable = true
                        }
                        lastErrorMessage = appendSentModelTag(res.userMessage, model)
                        if (attempt < maxRetries) {
                            delayFn(retryDelayMs)
                        }
                    }
                    is GroqAttemptResult.ModelNotFoundFailure -> {
                        lastErrorMessage = appendSentModelTag(res.userMessage, model)
                        moveToNextModel = true
                    }
                    is GroqAttemptResult.NonRetryableFailure -> {
                        return GeminiCoachService.GeminiHttpResult.Failure(
                            userMessage = appendSentModelTag(res.userMessage, model),
                            isOverloaded = false
                        )
                    }
                }
                if (moveToNextModel) break
            }
        }

        return GeminiCoachService.GeminiHttpResult.Failure(
            userMessage = lastErrorMessage,
            isOverloaded = sawServiceUnavailable
        )
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
            val apiCall = callGroqWithFallback(
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
                        friendlyMessage = appendSentModelTag(
                            "Format respons Groq AI kurang sesuai — menampilkan saran aturan lokal.",
                            apiCall.modelUsed
                        )
                    )
                }
            }
            is GeminiCoachService.GeminiHttpResult.Failure -> {
                DailySuggestionsResult.FallbackToLocal(
                    friendlyMessage = appendSentModelTag(apiCall.userMessage, GROQ_MODEL_NAME),
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
            val apiCall = callGroqWithFallback(
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                jsonMode = false
            )
        ) {
            is GeminiCoachService.GeminiHttpResult.Success -> {
                val cleaned = apiCall.text.trim()
                if (cleaned.isEmpty()) {
                    WeeklyReviewResult.Error(
                        appendSentModelTag(
                            "Groq AI tidak mengembalikan teks evaluasi. Coba lagi sebentar lagi.",
                            apiCall.modelUsed
                        )
                    )
                } else {
                    WeeklyReviewResult.Success(cleaned)
                }
            }
            is GeminiCoachService.GeminiHttpResult.Failure -> {
                WeeklyReviewResult.Error(appendSentModelTag(apiCall.userMessage, GROQ_MODEL_NAME))
            }
        }
    }

    private suspend fun callGroqWithFallback(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        jsonMode: Boolean
    ): GeminiCoachService.GeminiHttpResult {
        val cleanKey = apiKey.trim()
        return executeGroqWithFallback(
            models = GROQ_MODEL_LIST,
            invoker = { model ->
                callGroqSingleAttempt(
                    model = model,
                    apiKey = cleanKey,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    jsonMode = jsonMode
                )
            }
        )
    }

    private fun callGroqSingleAttempt(
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        jsonMode: Boolean
    ): GroqAttemptResult {
        // Kunci ketat ke "llama-3.3-70b-versatile" atau cadangan "llama-3.1-8b-instant" saja
        val enforcedModel = sanitizeGroqModel(model)
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(GROQ_ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                setRequestProperty("Content-Type", "application/json")
            }

            val requestBody = buildGroqRequestJson(
                model = enforcedModel,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                jsonMode = jsonMode
            )
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val statusCode = connection.responseCode
            if (statusCode == HttpURLConnection.HTTP_OK) {
                val rawBody = GeminiCoachService.readBoundedStream(connection.inputStream)
                val extracted = extractGroqContent(rawBody)
                return if (extracted.isNullOrBlank()) {
                    GroqAttemptResult.NonRetryableFailure(
                        appendSentModelTag(
                            "Respons Groq AI kosong. Menampilkan saran lokal.",
                            enforcedModel
                        )
                    )
                } else {
                    GroqAttemptResult.Success(extracted)
                }
            }

            val rawError = try {
                connection.errorStream?.let { GeminiCoachService.readBoundedStream(it) }
            } catch (_: Exception) {
                null
            }
            val serverMessage = extractServerErrorMessage(rawError)
            val diagnostic = formatHttpDiagnostic(
                statusCode = statusCode,
                endpointUrl = GROQ_ENDPOINT,
                sentModel = enforcedModel,
                serverErrorMessage = serverMessage
            )

            when (statusCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    GroqAttemptResult.ModelNotFoundFailure(diagnostic)
                }
                HttpURLConnection.HTTP_UNAVAILABLE -> {
                    GroqAttemptResult.RetryableFailure(
                        statusCode = HttpURLConnection.HTTP_UNAVAILABLE,
                        userMessage = diagnostic
                    )
                }
                429 -> {
                    GroqAttemptResult.RetryableFailure(
                        statusCode = 429,
                        userMessage = diagnostic
                    )
                }
                else -> {
                    GroqAttemptResult.NonRetryableFailure(diagnostic)
                }
            }
        } catch (e: Exception) {
            val detail = e.message?.trim()?.take(100) ?: "Periksa koneksi internetmu."
            GroqAttemptResult.NonRetryableFailure(
                appendSentModelTag(
                    "Gagal terhubung ke api.groq.com/openai/v1/chat/completions: $detail",
                    enforcedModel
                )
            )
        } finally {
            connection?.disconnect()
        }
    }

    internal fun buildGroqRequestJson(
        model: String = GROQ_MODEL_NAME,
        systemPrompt: String,
        userPrompt: String,
        jsonMode: Boolean
    ): String {
        val safeModel = sanitizeGroqModel(model)
        val payload = linkedMapOf<String, Any?>(
            "model" to safeModel,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            ),
            "temperature" to 0.7
        )

        if (jsonMode) {
            payload["response_format"] = mapOf("type" to "json_object")
        }

        return moshiMapAdapter.toJson(payload)
    }

    internal fun extractGroqContent(rawResponse: String): String? {
        return try {
            val root = moshiMapAdapter.fromJson(rawResponse.trim()) ?: return null
            val choices = root["choices"] as? List<*> ?: return null
            if (choices.isEmpty()) return null
            val firstChoice = choices[0] as? Map<*, *> ?: return null
            val message = firstChoice["message"] as? Map<*, *> ?: return null
            val content = message["content"] as? String ?: return null
            content.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
