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
        modelSummary = "openai/gpt-oss-20b"
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
 *   dengan model default "openai/gpt-oss-20b" (dapat diubah di Pengaturan HP),
 *   cadangan pertama "openai/gpt-oss-120b", max_tokens = 2048 tanpa response_format,
 *   parser reasoning (<think>...</think> + fence + substring { ... }), dan fallback aman ke /models
 *   hanya jika kedua model utama gagal akibat error model (404/400).
 * - Auto-routing: API Key yang diawali "gsk_" wajib diarahkan ke endpoint Groq.
 * - Gemini (Cadangan): delegasi ke [GeminiCoachService] dengan model Gemini Flash terpisah.
 */
object AiCoachService {

    const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    const val GROQ_MODELS_ENDPOINT = "https://api.groq.com/openai/v1/models"
    const val GROQ_MODEL_NAME = "openai/gpt-oss-20b"
    const val GROQ_FALLBACK_MODEL_NAME = "openai/gpt-oss-120b"
    const val GROQ_MAX_TOKENS = 2048

    val GROQ_PRESET_MODELS: List<String> = listOf(
        GROQ_MODEL_NAME,
        GROQ_FALLBACK_MODEL_NAME
    )

    val GROQ_MODEL_BLACKLIST: List<String> = listOf(
        "orpheus",
        "whisper",
        "tts",
        "audio",
        "speech",
        "guard",
        "playai",
        "arabic",
        "vision",
        "compound",
        "embed",
        "rerank",
        "moderation",
        "safeguard",
        "prompt"
    )

    val GROQ_MODEL_WHITELIST_PRIORITY: List<String> = listOf(
        "gpt-oss",
        "qwen",
        "deepseek",
        "kimi",
        "gemma",
        "mistral",
        "llama"
    )

    private val LEGACY_LLAMA_EXACT: Set<String> = setOf(
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "llama3-70b-8192",
        "llama3-8b-8192"
    )

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 40_000

    private val THINK_BLOCK_REGEX = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
    private val UNCLOSED_THINK_REGEX = Regex("<think>[\\s\\S]*$", RegexOption.IGNORE_CASE)

    private val moshiMapAdapter = Moshi.Builder().build().adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    /**
     * Memeriksa apakah ID model Groq mengandung kata terlarang (audio/TTS/guard/vision/embed/dll.)
     * atau merupakan model Gemini.
     */
    fun isBlacklistedGroqModel(modelId: String?): Boolean {
        val lower = modelId?.trim()?.lowercase().orEmpty()
        if (lower.isEmpty()) return true
        if (lower.startsWith("gemini-")) return true
        return GROQ_MODEL_BLACKLIST.any { forbidden -> lower.contains(forbidden) }
    }

    /**
     * Mendeteksi nama model Llama versi lama agar otomatis dimigrasikan ke `openai/gpt-oss-20b`.
     */
    fun isLegacyLlamaModel(modelId: String?): Boolean {
        val lower = modelId?.trim()?.lowercase().orEmpty()
        if (lower.isEmpty()) return false
        return lower in LEGACY_LLAMA_EXACT ||
            lower.startsWith("llama-3.1-") ||
            lower.startsWith("llama-3.2-") ||
            lower.startsWith("llama-3.3-") ||
            lower.startsWith("llama3-")
    }

    /**
     * Membersihkan ID model Groq dari penyimpanan/input pengguna:
     * - Kosong, mengandung blacklist, atau model Llama lama (jika [migrateLegacyLlama] = true)
     *   otomatis dikembalikan ke `openai/gpt-oss-20b`.
     */
    fun sanitizeGroqModel(
        rawModel: String?,
        migrateLegacyLlama: Boolean = true
    ): String {
        val candidate = rawModel?.trim().orEmpty()
        if (candidate.isEmpty()) return GROQ_MODEL_NAME
        if (isBlacklistedGroqModel(candidate)) return GROQ_MODEL_NAME
        if (migrateLegacyLlama && isLegacyLlamaModel(candidate)) return GROQ_MODEL_NAME
        return candidate
    }

    /**
     * Memfilter & mengurutkan daftar model dari `GET /openai/v1/models` dengan aturan ketat:
     * - WAJIB TOLAK seluruh model yang mengandung kata blacklist.
     * - WAJIB cocok dengan salah satu whitelist prioritas (`gpt-oss`, `qwen`, `deepseek`,
     *   `kimi`, `gemma`, `mistral`, `llama`) lalu diurutkan sesuai urutan prioritas tersebut.
     */
    internal fun selectBestFallbackModelsFromListing(
        modelIds: List<String>,
        alreadyTried: Set<String> = emptySet()
    ): List<String> {
        val triedLower = alreadyTried.map { it.trim().lowercase() }.toSet()
        return modelIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .filter { id -> id.lowercase() !in triedLower }
            .filter { id -> !isBlacklistedGroqModel(id) }
            .mapNotNull { id ->
                val lower = id.lowercase()
                val priority = GROQ_MODEL_WHITELIST_PRIORITY.indexOfFirst { keyword ->
                    lower.contains(keyword)
                }
                if (priority >= 0) id to priority else null
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    internal fun parseGroqModelsListJson(rawResponse: String): List<String> {
        return try {
            val root = moshiMapAdapter.fromJson(rawResponse.trim()) ?: return emptyList()
            val data = root["data"] as? List<*> ?: return emptyList()
            data.mapNotNull { item ->
                val obj = item as? Map<*, *> ?: return@mapNotNull null
                (obj["id"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Membersihkan output model reasoning (`gpt-oss`, DeepSeek, Qwen):
     * menghapus blok `<think>...</think>` dan markdown code fence (```json ... ```).
     */
    internal fun cleanReasoningText(raw: String): String {
        val withoutThink = raw
            .replace(THINK_BLOCK_REGEX, "")
            .replace(UNCLOSED_THINK_REGEX, "")
            .trim()
        return stripMarkdownFences(withoutThink)
    }

    /**
     * Membersihkan output reasoning lalu mengambil substring dari `{` pertama sampai `}` terakhir
     * agar JSON tetap valid walau ada teks pengantar/penutup dari model reasoning.
     */
    internal fun extractJsonFromReasoningContent(raw: String): String {
        val cleaned = cleanReasoningText(raw)
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        return if (firstBrace >= 0 && lastBrace > firstBrace) {
            cleaned.substring(firstBrace, lastBrace + 1).trim()
        } else {
            cleaned
        }
    }

    private fun stripMarkdownFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.contains("```")) return trimmed
        val fenceRegex = Regex("```[a-zA-Z0-9_-]*\\s*([\\s\\S]*?)```")
        val match = fenceRegex.find(trimmed)
        if (match != null) {
            val inside = match.groupValues[1].trim()
            if (inside.isNotEmpty()) return inside
        }
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /**
     * Menentukan apakah respons HTTP 404/400 dari Groq berkaitan dengan error model
     * (mis. model tidak ditemukan, decommissioned, atau butuh persetujuan terms).
     */
    internal fun isModelRelatedHttpError(
        statusCode: Int,
        serverErrorMessage: String?,
        rawErrorBody: String? = null
    ): Boolean {
        if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) return true
        if (statusCode != HttpURLConnection.HTTP_BAD_REQUEST) return false

        val combined = buildString {
            if (!serverErrorMessage.isNullOrBlank()) append(serverErrorMessage.lowercase()).append(' ')
            if (!rawErrorBody.isNullOrBlank()) append(rawErrorBody.lowercase())
        }.trim()

        if (combined.contains("invalid api key") ||
            combined.contains("api key") ||
            combined.contains("unauthorized")
        ) {
            return false
        }

        if (combined.isEmpty()) return true
        val modelErrorHints = listOf(
            "model",
            "decommissioned",
            "not found",
            "does not exist",
            "terms",
            "deprecated",
            "unsupported",
            "unavailable",
            "access"
        )
        return modelErrorHints.any { combined.contains(it) }
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
        return if (cleaned.contains("[Model terkirim:") || cleaned.contains("[Model dicoba:")) {
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

    private fun enrichWithTriedModels(message: String, triedModels: List<String>): String {
        val uniqueTried = triedModels.distinct()
        if (uniqueTried.size <= 1) return message
        val triedSummary = "[Model dicoba: ${uniqueTried.joinToString(" -> ")}]"
        return if (message.contains("[Model dicoba:")) {
            message
        } else {
            "$message $triedSummary"
        }
    }

    suspend fun generateDailySuggestions(
        provider: AiProvider = AiProvider.GROQ,
        apiKey: String,
        sessions: List<WorkoutSession>,
        energyState: TodayStatus,
        nowMillis: Long = System.currentTimeMillis(),
        groqModel: String = GROQ_MODEL_NAME,
        onGroqModelAutoUpdated: (String) -> Unit = {}
    ): DailyRecommendation? {
        return when (
            val result = generateDailySuggestionsDetailed(
                provider = provider,
                apiKey = apiKey,
                sessions = sessions,
                energyState = energyState,
                nowMillis = nowMillis,
                groqModel = groqModel,
                onGroqModelAutoUpdated = onGroqModelAutoUpdated
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
        nowMillis: Long = System.currentTimeMillis(),
        groqModel: String = GROQ_MODEL_NAME,
        onGroqModelAutoUpdated: (String) -> Unit = {}
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
                nowMillis = nowMillis,
                groqModel = groqModel,
                onGroqModelAutoUpdated = onGroqModelAutoUpdated
            )
        }
    }

    suspend fun generateWeeklyReview(
        provider: AiProvider = AiProvider.GROQ,
        apiKey: String,
        sessions: List<WorkoutSession>,
        nowMillis: Long = System.currentTimeMillis(),
        groqModel: String = GROQ_MODEL_NAME,
        onGroqModelAutoUpdated: (String) -> Unit = {}
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
                nowMillis = nowMillis,
                groqModel = groqModel,
                onGroqModelAutoUpdated = onGroqModelAutoUpdated
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
        data class ModelFailure(val statusCode: Int, val userMessage: String) : GroqAttemptResult()
        data class NonRetryableFailure(val userMessage: String) : GroqAttemptResult()
    }

    /**
     * Orkestrasi fallback anti-404 yang aman untuk Groq AI:
     * 1. Coba model pilihan dari Pengaturan (`openai/gpt-oss-20b` secara default).
     * 2. Jika gagal karena error model (HTTP 404 / 400 terkait model) atau rate limit/padat (429/503),
     *    otomatis coba cadangan `openai/gpt-oss-120b`.
     * 3. Hanya jika KEDUANYA gagal akibat error model (404/400), panggil `GET /openai/v1/models`
     *    dengan filter blacklist ketat & prioritas whitelist (`gpt-oss`, `qwen`, `deepseek`, `kimi`,
     *    `gemma`, `mistral`, `llama`).
     * 4. Jika model fallback berhasil (HTTP 200), simpan ID model tersebut ke Pengaturan via
     *    [onActiveModelChanged].
     */
    internal suspend fun executeGroqWithFallback(
        preferredModel: String = GROQ_MODEL_NAME,
        backupModel: String = GROQ_FALLBACK_MODEL_NAME,
        maxRetries: Int = GeminiCoachService.MAX_RETRY_ATTEMPTS,
        retryDelayMs: Long = GeminiCoachService.RETRY_DELAY_MS,
        delayFn: suspend (Long) -> Unit = { delay(it) },
        fetchAvailableModels: suspend () -> List<String> = { emptyList() },
        onActiveModelChanged: (String) -> Unit = {},
        invoker: suspend (model: String) -> GroqAttemptResult
    ): GeminiCoachService.GeminiHttpResult {
        val primary = sanitizeGroqModel(preferredModel, migrateLegacyLlama = true)
        val secondary = if (primary.equals(backupModel, ignoreCase = true)) {
            GROQ_MODEL_NAME
        } else {
            sanitizeGroqModel(backupModel, migrateLegacyLlama = true)
        }
        val initialQueue = listOf(primary, secondary).distinct()

        val triedModels = mutableListOf<String>()
        var sawServiceUnavailable = false
        var allInitialFailedWithModelError = true
        var lastErrorMessage = appendSentModelTag(
            GeminiCoachService.OVERLOADED_FRIENDLY_MESSAGE,
            primary
        )

        suspend fun trySingleModel(model: String): Pair<GeminiCoachService.GeminiHttpResult?, Boolean> {
            triedModels.add(model)
            val retriesForModel = maxRetries.coerceAtLeast(0)
            for (attempt in 0..retriesForModel) {
                when (val res = invoker(model)) {
                    is GroqAttemptResult.Success -> {
                        if (!model.equals(primary, ignoreCase = true)) {
                            onActiveModelChanged(model)
                        }
                        return GeminiCoachService.GeminiHttpResult.Success(
                            text = res.text,
                            modelUsed = model
                        ) to false
                    }
                    is GroqAttemptResult.ModelFailure -> {
                        lastErrorMessage = appendSentModelTag(res.userMessage, model)
                        return null to true
                    }
                    is GroqAttemptResult.RetryableFailure -> {
                        if (res.statusCode == HttpURLConnection.HTTP_UNAVAILABLE) {
                            sawServiceUnavailable = true
                        }
                        lastErrorMessage = appendSentModelTag(res.userMessage, model)
                        // Pada 429 (rate limit), langsung pindah ke model cadangan setelah maksimal 1 retry singkat
                        val effectiveMaxRetries = if (res.statusCode == 429) {
                            minOf(1, retriesForModel)
                        } else {
                            retriesForModel
                        }
                        if (attempt < effectiveMaxRetries) {
                            delayFn(retryDelayMs)
                        } else {
                            return null to false
                        }
                    }
                    is GroqAttemptResult.NonRetryableFailure -> {
                        val finalMsg = enrichWithTriedModels(
                            appendSentModelTag(res.userMessage, model),
                            triedModels
                        )
                        return GeminiCoachService.GeminiHttpResult.Failure(
                            userMessage = finalMsg,
                            isOverloaded = false
                        ) to false
                    }
                }
            }
            return null to false
        }

        for (model in initialQueue) {
            val (terminalResult, wasModelError) = trySingleModel(model)
            if (terminalResult != null) {
                return terminalResult
            }
            if (!wasModelError) {
                allInitialFailedWithModelError = false
            }
        }

        // Hanya panggil GET /openai/v1/models jika KEDUA model awal gagal karena error model (404/400)
        if (allInitialFailedWithModelError && initialQueue.isNotEmpty()) {
            val discovered = fetchAvailableModels()
            val candidates = selectBestFallbackModelsFromListing(
                modelIds = discovered,
                alreadyTried = triedModels.toSet()
            ).take(2)

            for (candidate in candidates) {
                val (terminalResult, _) = trySingleModel(candidate)
                if (terminalResult != null) {
                    return terminalResult
                }
            }
        }

        return GeminiCoachService.GeminiHttpResult.Failure(
            userMessage = enrichWithTriedModels(lastErrorMessage, triedModels),
            isOverloaded = sawServiceUnavailable
        )
    }

    private suspend fun generateGroqDailySuggestions(
        apiKey: String,
        sessions: List<WorkoutSession>,
        energyState: TodayStatus,
        nowMillis: Long,
        groqModel: String,
        onGroqModelAutoUpdated: (String) -> Unit
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
                preferredModel = groqModel,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                expectJson = true,
                onActiveModelChanged = onGroqModelAutoUpdated
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
                    friendlyMessage = appendSentModelTag(apiCall.userMessage, sanitizeGroqModel(groqModel)),
                    isOverloaded = apiCall.isOverloaded
                )
            }
        }
    }

    private suspend fun generateGroqWeeklyReview(
        apiKey: String,
        sessions: List<WorkoutSession>,
        nowMillis: Long,
        groqModel: String,
        onGroqModelAutoUpdated: (String) -> Unit
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
                preferredModel = groqModel,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                expectJson = false,
                onActiveModelChanged = onGroqModelAutoUpdated
            )
        ) {
            is GeminiCoachService.GeminiHttpResult.Success -> {
                val cleaned = cleanReasoningText(apiCall.text)
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
                WeeklyReviewResult.Error(
                    appendSentModelTag(apiCall.userMessage, sanitizeGroqModel(groqModel))
                )
            }
        }
    }

    private suspend fun callGroqWithFallback(
        apiKey: String,
        preferredModel: String,
        systemPrompt: String,
        userPrompt: String,
        expectJson: Boolean,
        onActiveModelChanged: (String) -> Unit
    ): GeminiCoachService.GeminiHttpResult {
        val cleanKey = apiKey.trim()
        return executeGroqWithFallback(
            preferredModel = preferredModel,
            backupModel = GROQ_FALLBACK_MODEL_NAME,
            fetchAvailableModels = { fetchGroqModels(cleanKey) },
            onActiveModelChanged = onActiveModelChanged,
            invoker = { model ->
                callGroqSingleAttempt(
                    model = model,
                    apiKey = cleanKey,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    expectJson = expectJson
                )
            }
        )
    }

    private fun fetchGroqModels(apiKey: String): List<String> {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(GROQ_MODELS_ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = CONNECT_TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val rawBody = GeminiCoachService.readBoundedStream(connection.inputStream)
                parseGroqModelsListJson(rawBody)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private fun callGroqSingleAttempt(
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        expectJson: Boolean
    ): GroqAttemptResult {
        val safeModel = sanitizeGroqModel(model, migrateLegacyLlama = false)
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
                model = safeModel,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt
            )
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val statusCode = connection.responseCode
            if (statusCode == HttpURLConnection.HTTP_OK) {
                val rawBody = GeminiCoachService.readBoundedStream(connection.inputStream)
                val extracted = extractGroqContent(
                    rawResponse = rawBody,
                    extractJsonObject = expectJson
                )
                return if (extracted.isNullOrBlank()) {
                    GroqAttemptResult.NonRetryableFailure(
                        appendSentModelTag(
                            "Respons Groq AI kosong. Menampilkan saran lokal.",
                            safeModel
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
                sentModel = safeModel,
                serverErrorMessage = serverMessage
            )

            when {
                isModelRelatedHttpError(statusCode, serverMessage, rawError) -> {
                    GroqAttemptResult.ModelFailure(
                        statusCode = statusCode,
                        userMessage = diagnostic
                    )
                }
                statusCode == HttpURLConnection.HTTP_UNAVAILABLE -> {
                    GroqAttemptResult.RetryableFailure(
                        statusCode = HttpURLConnection.HTTP_UNAVAILABLE,
                        userMessage = diagnostic
                    )
                }
                statusCode == 429 -> {
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
                    safeModel
                )
            )
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Membangun payload JSON untuk endpoint OpenAI-compatible Groq dengan `max_tokens = 2048`
     * dan TANPA `response_format` agar kompatibel penuh dengan model reasoning (`openai/gpt-oss-*`).
     */
    internal fun buildGroqRequestJson(
        model: String = GROQ_MODEL_NAME,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val safeModel = sanitizeGroqModel(model, migrateLegacyLlama = false)
        val payload = linkedMapOf<String, Any?>(
            "model" to safeModel,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            ),
            "temperature" to 0.7,
            "max_tokens" to GROQ_MAX_TOKENS
        )
        return moshiMapAdapter.toJson(payload)
    }

    internal fun extractGroqContent(
        rawResponse: String,
        extractJsonObject: Boolean = false
    ): String? {
        return try {
            val root = moshiMapAdapter.fromJson(rawResponse.trim()) ?: return null
            val choices = root["choices"] as? List<*> ?: return null
            if (choices.isEmpty()) return null
            val firstChoice = choices[0] as? Map<*, *> ?: return null
            val message = firstChoice["message"] as? Map<*, *> ?: return null
            val content = message["content"] as? String ?: return null
            val processed = if (extractJsonObject) {
                extractJsonFromReasoningContent(content)
            } else {
                cleanReasoningText(content)
            }
            processed.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
