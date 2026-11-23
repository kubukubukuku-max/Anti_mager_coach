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
 * - Groq (Default): endpoint https://api.groq.com/openai/v1/chat/completions
 *   dengan model utama llama-3.3-70b-versatile, cadangan llama-3.1-8b-instant,
 *   serta auto-discovery via GET /openai/v1/models bila menerima HTTP 404.
 * - Auto-routing: API Key yang diawali "gsk_" otomatis dipaksa ke endpoint Groq.
 * - Gemini (Cadangan): delegasi ke [GeminiCoachService] dengan auto-retry & fallback model Flash.
 */
object AiCoachService {

    const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    const val GROQ_MODELS_ENDPOINT = "https://api.groq.com/openai/v1/models"
    const val GROQ_MODEL_NAME = "llama-3.3-70b-versatile"
    const val GROQ_FALLBACK_MODEL_NAME = "llama-3.1-8b-instant"

    val GROQ_MODEL_LIST: List<String> = listOf(
        GROQ_MODEL_NAME,
        GROQ_FALLBACK_MODEL_NAME
    )

    private val EXCLUDED_GROQ_MODEL_KEYWORDS = listOf(
        "whisper",
        "tts",
        "guard",
        "audio"
    )

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 35_000

    private val moshiMapAdapter = Moshi.Builder().build().adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    /**
     * Memastikan API Key yang diawali `gsk_` selalu diarahkan ke endpoint Groq,
     * meskipun pengguna sebelumnya memilih tab Gemini.
     */
    fun resolveEffectiveProvider(requestedProvider: AiProvider, apiKey: String): AiProvider {
        val cleanKey = apiKey.trim()
        return if (cleanKey.startsWith("gsk_")) {
            AiProvider.GROQ
        } else {
            requestedProvider
        }
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
     * Mengekstrak `error.message` (atau `message`) secara ringkas dari respons error JSON server
     * agar bisa ditampilkan singkat di UI sebelum fallback ke CoachingEngine lokal.
     */
    internal fun extractServerErrorMessage(rawErrorBody: String?): String? {
        if (rawErrorBody.isNullOrBlank()) return null
        return try {
            val root = moshiMapAdapter.fromJson(rawErrorBody.trim()) ?: return null
            val errorObj = root["error"] as? Map<*, *>
            val nestedMessage = (errorObj?.get("message") as? String)?.trim()
            val topMessage = (root["message"] as? String)?.trim()
            val chosen = when {
                !nestedMessage.isNullOrEmpty() -> nestedMessage
                !topMessage.isNullOrEmpty() -> topMessage
                else -> null
            }
            chosen?.replace(Regex("\\s+"), " ")?.take(140)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Memilih model chat aktif pertama dari JSON respons `GET https://api.groq.com/openai/v1/models`,
     * mengabaikan model yang mengandung kata `whisper`, `tts`, `guard`, atau `audio`,
     * serta melewati model yang sudah dicoba sebelumnya.
     */
    internal fun selectFirstActiveGroqChatModel(
        modelsResponseJson: String,
        excludeModels: Set<String> = emptySet()
    ): String? {
        return try {
            val root = moshiMapAdapter.fromJson(modelsResponseJson.trim()) ?: return null
            val data = root["data"] as? List<*> ?: return null
            val excludedLower = excludeModels.map { it.lowercase() }.toSet()

            for (entry in data) {
                val item = entry as? Map<*, *> ?: continue
                val activeVal = item["active"]
                if (activeVal is Boolean && !activeVal) continue
                val id = (item["id"] as? String)?.trim().orEmpty()
                if (id.isEmpty()) continue
                val lower = id.lowercase()
                if (lower in excludedLower) continue
                if (EXCLUDED_GROQ_MODEL_KEYWORDS.any { keyword -> lower.contains(keyword) }) {
                    continue
                }
                return id
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    internal sealed class GroqAttemptResult {
        data class Success(val text: String) : GroqAttemptResult()
        data class RetryableFailure(val statusCode: Int, val userMessage: String) : GroqAttemptResult()
        data class ModelNotFoundFailure(val userMessage: String) : GroqAttemptResult()
        data class NonRetryableFailure(val userMessage: String) : GroqAttemptResult()
    }

    /**
     * Orkestrasi panggilan Groq AI:
     * 1. Coba berurutan `llama-3.3-70b-versatile` lalu `llama-3.1-8b-instant`.
     * 2. Jika 503/429 -> auto-retry hingga [maxRetries] kali dengan jeda [retryDelayMs], lalu pindah model cadangan.
     * 3. Jika 404 -> langsung coba model cadangan; jika semua model default 404, otomatis panggil
     *    `GET /openai/v1/models` untuk menemukan model chat aktif pertama lalu ulangi request.
     * 4. Jika tetap gagal -> kembalikan pesan singkat server sebelum fallback lokal.
     */
    internal suspend fun executeGroqWithFallbackAndDiscovery(
        models: List<String> = GROQ_MODEL_LIST,
        maxRetries: Int = GeminiCoachService.MAX_RETRY_ATTEMPTS,
        retryDelayMs: Long = GeminiCoachService.RETRY_DELAY_MS,
        delayFn: suspend (Long) -> Unit = { delay(it) },
        discoverChatModelFn: suspend (triedModels: Set<String>) -> String?,
        invoker: suspend (model: String) -> GroqAttemptResult
    ): GeminiCoachService.GeminiHttpResult {
        val triedModels = linkedSetOf<String>()
        var saw404 = false
        var sawServiceUnavailable = false
        var lastErrorMessage = GeminiCoachService.OVERLOADED_FRIENDLY_MESSAGE

        suspend fun runSingleModel(model: String): GeminiCoachService.GeminiHttpResult? {
            triedModels.add(model)
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
                        lastErrorMessage = res.userMessage
                        if (attempt < maxRetries) {
                            delayFn(retryDelayMs)
                        }
                    }
                    is GroqAttemptResult.ModelNotFoundFailure -> {
                        saw404 = true
                        lastErrorMessage = res.userMessage
                        // Move immediately to next fallback model without retrying the missing model
                        return null
                    }
                    is GroqAttemptResult.NonRetryableFailure -> {
                        return GeminiCoachService.GeminiHttpResult.Failure(
                            userMessage = res.userMessage,
                            isOverloaded = false
                        )
                    }
                }
            }
            return null
        }

        for (model in models) {
            val outcome = runSingleModel(model)
            if (outcome != null) return outcome
        }

        // If server returned HTTP 404 for default models, discover an active chat model and retry
        if (saw404) {
            val discoveredModel = discoverChatModelFn(triedModels)
            if (!discoveredModel.isNullOrBlank() && discoveredModel !in triedModels) {
                val discoveredOutcome = runSingleModel(discoveredModel)
                if (discoveredOutcome != null) return discoveredOutcome
            }
        }

        val finalMessage = if (sawServiceUnavailable && !saw404) {
            GeminiCoachService.OVERLOADED_FRIENDLY_MESSAGE
        } else {
            lastErrorMessage
        }
        return GeminiCoachService.GeminiHttpResult.Failure(
            userMessage = finalMessage,
            isOverloaded = sawServiceUnavailable && !saw404
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

    private suspend fun callGroqWithFallback(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        jsonMode: Boolean
    ): GeminiCoachService.GeminiHttpResult {
        val cleanKey = apiKey.trim()
        return executeGroqWithFallbackAndDiscovery(
            models = GROQ_MODEL_LIST,
            discoverChatModelFn = { triedModels ->
                fetchActiveGroqChatModel(apiKey = cleanKey, excludeModels = triedModels)
            },
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

    private fun fetchActiveGroqChatModel(
        apiKey: String,
        excludeModels: Set<String>
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(GROQ_MODELS_ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                setRequestProperty("Content-Type", "application/json")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val body = GeminiCoachService.readBoundedStream(connection.inputStream)
                selectFirstActiveGroqChatModel(body, excludeModels)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun callGroqSingleAttempt(
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        jsonMode: Boolean
    ): GroqAttemptResult {
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
                model = model,
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
                        "Respons Groq AI kosong. Menampilkan saran lokal."
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

            when (statusCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    val detail = serverMessage?.let { "Groq AI (404): $it" }
                        ?: "Model Groq AI tidak ditemukan (404)."
                    GroqAttemptResult.ModelNotFoundFailure(detail)
                }
                HttpURLConnection.HTTP_UNAVAILABLE -> {
                    GroqAttemptResult.RetryableFailure(
                        statusCode = HttpURLConnection.HTTP_UNAVAILABLE,
                        userMessage = serverMessage?.let { "Groq AI (503): $it" }
                            ?: GeminiCoachService.OVERLOADED_FRIENDLY_MESSAGE
                    )
                }
                429 -> {
                    GroqAttemptResult.RetryableFailure(
                        statusCode = 429,
                        userMessage = serverMessage?.let { "Kuota Groq AI penuh (429): $it" }
                            ?: "Kuota Groq AI sementara penuh. Coba tekan Refresh sebentar lagi ya."
                    )
                }
                HttpURLConnection.HTTP_BAD_REQUEST,
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    val detail = serverMessage?.let { "Groq AI ($statusCode): $it" }
                        ?: "API Key Groq tidak valid atau belum aktif (kode $statusCode). Periksa di Pengaturan."
                    GroqAttemptResult.NonRetryableFailure(detail)
                }
                else -> {
                    val detail = serverMessage?.let { "Groq AI ($statusCode): $it" }
                        ?: "Layanan Groq AI sedang tidak merespons (kode $statusCode)."
                    GroqAttemptResult.NonRetryableFailure(detail)
                }
            }
        } catch (_: Exception) {
            GroqAttemptResult.NonRetryableFailure(
                "Tidak dapat terhubung ke Groq AI. Periksa koneksi internetmu."
            )
        } finally {
            connection?.disconnect()
        }
    }

    internal fun buildGroqRequestJson(
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
