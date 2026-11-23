package com.example

import com.example.domain.CoachingEngine
import com.example.domain.DateTimeHelper
import com.example.model.DailyRecommendation
import com.example.model.DumbbellItem
import com.example.model.SportType
import com.example.model.SuggestionOption
import com.example.model.TodayStatus
import com.example.model.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

sealed class DailySuggestionsResult {
    data class Success(val recommendation: DailyRecommendation) : DailySuggestionsResult()
    data class FallbackToLocal(
        val friendlyMessage: String,
        val isOverloaded: Boolean = false
    ) : DailySuggestionsResult()
}

sealed class WeeklyReviewResult {
    data class Success(val reviewText: String) : WeeklyReviewResult()
    data class Error(val message: String) : WeeklyReviewResult()
}

object GeminiCoachService {

    /**
     * Daftar model Gemini Flash berurutan:
     * 1. Model Flash utama yang aktif & direkomendasikan saat ini.
     * 2. Model Flash alternatif/lite yang stabil sebagai cadangan (fallback) bila server utama 503/429.
     */
    val MODEL_LIST: List<String> = listOf(
        "gemini-3.8-flash",
        "gemini-3.5-flash-lite"
    )

    /**
     * Model utama (elemen pertama MODEL_LIST) untuk referensi cepat.
     */
    val MODEL_NAME: String
        get() = MODEL_LIST.first()

    const val MAX_RETRY_ATTEMPTS = 2
    const val RETRY_DELAY_MS = 1_500L
    const val OVERLOADED_FRIENDLY_MESSAGE =
        "Server AI lagi padat sebentar, coba tekan Refresh lagi ya"

    private const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 35_000
    private const val MAX_RESPONSE_CHARS = 512 * 1024

    /**
     * Merangkum riwayat latihan dalam rentang [daysWindow] hari terakhir menjadi teks ringkas
     * agar hemat token namun kaya konteks untuk prompt Gemini.
     */
    fun summarizeSessions(
        sessions: List<WorkoutSession>,
        daysWindow: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val recent = sessions.mapNotNull { session ->
            val millis = DateTimeHelper.parseIsoToMillis(session.startedAtIso) ?: return@mapNotNull null
            val dayDiff = DateTimeHelper.calendarDaysBetween(millis, nowMillis)
            if (dayDiff in 0 until daysWindow) Triple(session, millis, dayDiff) else null
        }.sortedByDescending { it.second }

        if (recent.isEmpty()) {
            return "Belum ada sesi latihan tercatat dalam $daysWindow hari terakhir."
        }

        val sb = StringBuilder()
        recent.forEach { (session, millis, dayDiff) ->
            val dateKey = DateTimeHelper.toLocalDateKey(millis)
            val relative = when (dayDiff) {
                0 -> "Hari ini"
                1 -> "Kemarin"
                else -> "$dayDiff hari lalu"
            }
            sb.append("- $dateKey ($relative): ${session.sportType.displayName} [${session.sportType.name}], ")
            sb.append("${session.durationMin} menit, RPE ${session.rpe}/10")
            if (session.distanceKm != null && session.distanceKm > 0.0) {
                sb.append(", jarak ${session.distanceKm} km")
            }
            if (session.dumbbellItems.isNotEmpty()) {
                val names = session.dumbbellItems.take(4).joinToString(", ") { it.exerciseName }
                sb.append(", gerakan: $names")
            }
            val cleanNote = session.notes?.trim()?.take(80)
            if (!cleanNote.isNullOrEmpty()) {
                sb.append(", catatan: \"$cleanNote\"")
            }
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    /**
     * Menghasilkan saran harian berbasis Gemini AI dalam format JSON ketat.
     * Mengembalikan null apabila offline, API Key kosong, atau gagal agar aplikasi fallback ke CoachingEngine lokal.
     */
    suspend fun generateDailySuggestions(
        apiKey: String,
        sessions: List<WorkoutSession>,
        energyState: TodayStatus,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyRecommendation? {
        return when (
            val result = generateDailySuggestionsDetailed(
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

    /**
     * Versi detail dari [generateDailySuggestions] yang juga mengembalikan pesan ramah
     * (misal saat HTTP 503 setelah retry & fallback model) agar UI tetap menampilkan saran lokal
     * sekaligus memberi info yang menenangkan ke pengguna.
     */
    suspend fun generateDailySuggestionsDetailed(
        apiKey: String,
        sessions: List<WorkoutSession>,
        energyState: TodayStatus,
        nowMillis: Long = System.currentTimeMillis()
    ): DailySuggestionsResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext DailySuggestionsResult.FallbackToLocal(
                friendlyMessage = "Isi Gemini API Key di Pengaturan terlebih dahulu."
            )
        }

        val history14Days = summarizeSessions(sessions, daysWindow = 14, nowMillis = nowMillis)
        val insights = CoachingEngine.analyzeInsights(sessions, nowMillis)

        val prompt = """
            Kamu adalah "Anti-Mager Coach", pelatih kebugaran pribadi yang santai, suportif, anti-stres, berbahasa Indonesia natural (tidak kaku), dan sangat paham keseimbangan kardio vs kekuatan (beban/bodyweight) serta pemulihan otot.

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

            Balas HANYA dalam JSON valid sesuai skema persis ini tanpa markdown:
            {
              "insightBadge": "...",
              "ideal": { "title": "...", "sportType": "RUNNING|BADMINTON|WALKING|DUMBBELL|REST", "durationMin": 25, "reason": "...", "guidance": "..." },
              "light": { "title": "...", "sportType": "RUNNING|BADMINTON|WALKING|DUMBBELL|REST", "durationMin": 12, "reason": "...", "guidance": "..." }
            }
        """.trimIndent()

        when (val apiCall = callGeminiApiDetailed(apiKey = cleanKey, prompt = prompt, jsonMode = true)) {
            is GeminiHttpResult.Success -> {
                val parsed = parseDailySuggestionJson(
                    rawJson = apiCall.text,
                    energyState = energyState,
                    sessions = sessions,
                    nowMillis = nowMillis,
                    insights = insights
                )
                if (parsed != null) {
                    DailySuggestionsResult.Success(parsed)
                } else {
                    DailySuggestionsResult.FallbackToLocal(
                        friendlyMessage = "Format respons AI kurang sesuai — menampilkan saran aturan lokal."
                    )
                }
            }
            is GeminiHttpResult.Failure -> {
                DailySuggestionsResult.FallbackToLocal(
                    friendlyMessage = apiCall.userMessage,
                    isOverloaded = apiCall.isOverloaded
                )
            }
        }
    }

    /**
     * Menghasilkan evaluasi mingguan singkat (maks 3 poin dengan emoji) bergaya teman.
     */
    suspend fun generateWeeklyReview(
        apiKey: String,
        sessions: List<WorkoutSession>,
        nowMillis: Long = System.currentTimeMillis()
    ): WeeklyReviewResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext WeeklyReviewResult.Error(
                "Gemini API Key belum diisi. Silakan masukkan API Key di Pengaturan terlebih dahulu."
            )
        }

        val summary7Days = summarizeSessions(sessions, daysWindow = 7, nowMillis = nowMillis)
        val rhythm = CoachingEngine.computeWeeklyRhythm(sessions, nowMillis)

        val prompt = """
            Kamu adalah "Anti-Mager Coach", teman olahraga yang hangat, suportif, santai, anti-stres, berbahasa Indonesia natural.

            Statistik 7 hari terakhir user:
            - Hari aktif: ${rhythm.activeDaysCount}/7 hari (${rhythm.consistencyPercent}%)
            - Total durasi aktif: ${rhythm.totalActiveMinutes} menit
            - Jumlah sesi kekuatan/beban: ${rhythm.strengthSessionCount}
            - Jumlah sesi kardio: ${rhythm.cardioSessionCount}

            Detail sesi 7 hari terakhir:
            $summary7Days

            Buat evaluasi mingguan singkat bergaya teman (MAKSIMAL 3 poin pendek, tiap poin 1-2 kalimat, masing-masing diawali emoji yang sesuai, tanpa tabel atau markdown rumit):
            1. Apresiasi minggu ini (WAJIB sebutkan angka atau jenis latihan asli dari data di atas; jika masih 0 sesi, beri semangat ramah untuk mulai pelan).
            2. Keseimbangan tubuh (komentar santai apakah porsi kardio, beban/bodyweight, dan istirahat sudah pas).
            3. 1 Fokus ringan untuk minggu depan yang realistis dan bebas tekanan.
        """.trimIndent()

        when (val apiCall = callGeminiApiDetailed(apiKey = cleanKey, prompt = prompt, jsonMode = false)) {
            is GeminiHttpResult.Success -> {
                val cleaned = apiCall.text.trim()
                if (cleaned.isEmpty()) {
                    WeeklyReviewResult.Error("Gemini tidak mengembalikan teks evaluasi. Coba lagi sebentar lagi.")
                } else {
                    WeeklyReviewResult.Success(cleaned)
                }
            }
            is GeminiHttpResult.Failure -> {
                WeeklyReviewResult.Error(apiCall.userMessage)
            }
        }
    }

    internal data class ParsedAiOption(
        val title: String,
        val sportType: String,
        val durationMin: Int,
        val reason: String,
        val guidance: String
    )

    internal fun buildDailyRecommendationFromParsed(
        insightBadge: String,
        ideal: ParsedAiOption,
        light: ParsedAiOption,
        energyState: TodayStatus,
        insights: com.example.model.WorkoutInsights,
        providerLabel: String = "Gemini AI"
    ): DailyRecommendation? {
        val idealTitle = ideal.title.trim()
        val lightTitle = light.title.trim()
        if (idealTitle.isEmpty() || lightTitle.isEmpty()) return null

        val idealSport = parseSportType(ideal.sportType)
        val lightSport = parseSportType(light.sportType)
        val idealDur = ideal.durationMin.coerceIn(5, 90)
        val lightDur = light.durationMin.coerceIn(5, 45)

        val contextSummary = when (energyState) {
            TodayStatus.FRESH -> "Dipersonalisasi $providerLabel dari riwayat 14 harimu saat kondisi senggang & fit."
            TodayStatus.TIRED -> "Dipersonalisasi $providerLabel agar tetap lembut dan mendukung pemulihan tubuhmu."
            TodayStatus.BUSY -> "Dipersonalisasi $providerLabel jadi micro-workout ringkas di tengah jadwal padat."
        }

        val idealOption = SuggestionOption(
            badge = "Ritme Utama",
            title = idealTitle,
            sportType = idealSport,
            estimatedDurationMin = idealDur,
            durationLabel = "$idealDur menit",
            reason = ideal.reason.trim().ifEmpty { "Disusun sesuai pola latihanmu dalam 14 hari terakhir." },
            relaxedGuidance = ideal.guidance.trim().ifEmpty { "Gerak santai sesuai ritme napasmu tanpa perlu memaksa." },
            defaultRpe = defaultRpeFor(idealSport, isIdeal = true, energyState = energyState),
            suggestedDumbbellItems = defaultSuggestedItemsFor(idealSport, idealTitle, isIdeal = true)
        )

        val lightOption = SuggestionOption(
            badge = "Anti-Mager",
            title = lightTitle,
            sportType = lightSport,
            estimatedDurationMin = lightDur,
            durationLabel = "$lightDur menit",
            reason = light.reason.trim().ifEmpty { "Versi paling ringan agar kamu tetap bergerak tanpa tekanan." },
            relaxedGuidance = light.guidance.trim().ifEmpty { "Cukup mulai pelan dan berhenti kapan pun tubuh butuh istirahat." },
            defaultRpe = defaultRpeFor(lightSport, isIdeal = false, energyState = energyState),
            suggestedDumbbellItems = defaultSuggestedItemsFor(lightSport, lightTitle, isIdeal = false)
        )

        return DailyRecommendation(
            idealOption = idealOption,
            lightOption = lightOption,
            contextSummary = contextSummary,
            insightBadge = insightBadge.trim().ifEmpty { "✨ Disusun khusus oleh Gemini AI" },
            insights = insights,
            isAiGenerated = true
        )
    }

    internal fun parseSportType(raw: String): SportType {
        return when (raw.trim().uppercase()) {
            "RUNNING", "LARI" -> SportType.RUNNING
            "BADMINTON", "BULUTANGKIS" -> SportType.BADMINTON
            "WALKING", "JALAN" -> SportType.WALKING
            "DUMBBELL", "STRENGTH", "BODYWEIGHT", "BEBAN" -> SportType.DUMBBELL
            "REST", "ISTIRAHAT" -> SportType.REST
            else -> SportType.WALKING
        }
    }

    internal fun parseDailySuggestionJson(
        rawJson: String,
        energyState: TodayStatus,
        sessions: List<WorkoutSession>,
        nowMillis: Long,
        insights: com.example.model.WorkoutInsights,
        providerLabel: String = "Gemini AI"
    ): DailyRecommendation? {
        return try {
            val cleaned = stripCodeFenceIfPresent(rawJson)
            val root = JSONObject(cleaned)
            val idealObj = root.optJSONObject("ideal") ?: return null
            val lightObj = root.optJSONObject("light") ?: return null

            val insightBadge = if (root.isNull("insightBadge")) "" else root.optString("insightBadge", "")
            val ideal = ParsedAiOption(
                title = if (idealObj.isNull("title")) "" else idealObj.optString("title", ""),
                sportType = if (idealObj.isNull("sportType")) "" else idealObj.optString("sportType", ""),
                durationMin = idealObj.optInt("durationMin", 20),
                reason = if (idealObj.isNull("reason")) "" else idealObj.optString("reason", ""),
                guidance = if (idealObj.isNull("guidance")) "" else idealObj.optString("guidance", "")
            )
            val light = ParsedAiOption(
                title = if (lightObj.isNull("title")) "" else lightObj.optString("title", ""),
                sportType = if (lightObj.isNull("sportType")) "" else lightObj.optString("sportType", ""),
                durationMin = lightObj.optInt("durationMin", 12),
                reason = if (lightObj.isNull("reason")) "" else lightObj.optString("reason", ""),
                guidance = if (lightObj.isNull("guidance")) "" else lightObj.optString("guidance", "")
            )

            buildDailyRecommendationFromParsed(
                insightBadge = insightBadge,
                ideal = ideal,
                light = light,
                energyState = energyState,
                insights = insights,
                providerLabel = providerLabel
            )
        } catch (_: Exception) {
            null
        }
    }

    internal fun stripCodeFenceIfPresent(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        if (firstNewline == -1) return trimmed
        val withoutStart = trimmed.substring(firstNewline + 1)
        val lastFence = withoutStart.lastIndexOf("```")
        return if (lastFence >= 0) withoutStart.substring(0, lastFence).trim() else withoutStart.trim()
    }

    private fun defaultRpeFor(sport: SportType, isIdeal: Boolean, energyState: TodayStatus): Int {
        return when {
            sport == SportType.REST -> 1
            !isIdeal -> if (sport == SportType.DUMBBELL) 3 else 2
            energyState == TodayStatus.TIRED -> 3
            sport == SportType.WALKING -> 3
            else -> 5
        }
    }

    private fun defaultSuggestedItemsFor(
        sport: SportType,
        title: String,
        isIdeal: Boolean
    ): List<DumbbellItem> {
        if (sport != SportType.DUMBBELL) return emptyList()
        val lower = title.lowercase()
        return when {
            lower.contains("upper") || lower.contains("atas") -> listOf(
                DumbbellItem("Dumbbell Floor Press", 5.0, 3, 10),
                DumbbellItem("Bent-Over Row", 5.0, 3, 10),
                DumbbellItem("Overhead Press", 4.0, 3, 10),
                DumbbellItem("Plank", 0.0, 3, 30)
            )
            lower.contains("core") || !isIdeal -> listOf(
                DumbbellItem("Push-up (atau Knee Push-up)", 0.0, 2, 8),
                DumbbellItem("Bird-Dog", 0.0, 2, 10),
                DumbbellItem("Plank", 0.0, 2, 30)
            )
            lower.contains("dumbbell") -> listOf(
                DumbbellItem("Goblet Squat", 5.0, 3, 10),
                DumbbellItem("Dumbbell Row", 5.0, 3, 10),
                DumbbellItem("Dumbbell Floor Press", 5.0, 3, 10),
                DumbbellItem("Bicep Curl", 4.0, 3, 10)
            )
            else -> listOf(
                DumbbellItem("Push-up (atau Knee Push-up)", 0.0, 3, 8),
                DumbbellItem("Bodyweight Squat", 0.0, 3, 12),
                DumbbellItem("Glute Bridge", 0.0, 3, 12),
                DumbbellItem("Plank", 0.0, 3, 30)
            )
        }
    }

    internal sealed class SingleAttemptResult {
        data class Success(val text: String) : SingleAttemptResult()
        data class RetryableFailure(val statusCode: Int, val userMessage: String) : SingleAttemptResult()
        data class NonRetryableFailure(val userMessage: String) : SingleAttemptResult()
    }

    internal sealed class GeminiHttpResult {
        data class Success(val text: String, val modelUsed: String) : GeminiHttpResult()
        data class Failure(val userMessage: String, val isOverloaded: Boolean = false) : GeminiHttpResult()
    }

    /**
     * Orkestrasi Auto-Retry (maksimal [maxRetries] kali dengan jeda [retryDelayMs] untuk HTTP 503/429)
     * dan otomatis berpindah ke model cadangan di [models] jika model utama tetap 503/429 setelah retry.
     */
    internal suspend fun executeWithRetryAndFallback(
        models: List<String> = MODEL_LIST,
        maxRetries: Int = MAX_RETRY_ATTEMPTS,
        retryDelayMs: Long = RETRY_DELAY_MS,
        delayFn: suspend (Long) -> Unit = { delay(it) },
        invoker: suspend (model: String) -> SingleAttemptResult
    ): GeminiHttpResult {
        var sawServiceUnavailable = false
        var lastRetryableMessage = OVERLOADED_FRIENDLY_MESSAGE

        for (model in models) {
            for (attempt in 0..maxRetries) {
                when (val attemptResult = invoker(model)) {
                    is SingleAttemptResult.Success -> {
                        return GeminiHttpResult.Success(
                            text = attemptResult.text,
                            modelUsed = model
                        )
                    }
                    is SingleAttemptResult.RetryableFailure -> {
                        if (attemptResult.statusCode == HttpURLConnection.HTTP_UNAVAILABLE) {
                            sawServiceUnavailable = true
                        }
                        lastRetryableMessage = attemptResult.userMessage
                        if (attempt < maxRetries) {
                            delayFn(retryDelayMs)
                        }
                    }
                    is SingleAttemptResult.NonRetryableFailure -> {
                        return GeminiHttpResult.Failure(
                            userMessage = attemptResult.userMessage,
                            isOverloaded = false
                        )
                    }
                }
            }
        }

        val finalMessage = if (sawServiceUnavailable) {
            OVERLOADED_FRIENDLY_MESSAGE
        } else {
            lastRetryableMessage
        }
        return GeminiHttpResult.Failure(
            userMessage = finalMessage,
            isOverloaded = true
        )
    }

    private suspend fun callGeminiApiDetailed(
        apiKey: String,
        prompt: String,
        jsonMode: Boolean
    ): GeminiHttpResult {
        return executeWithRetryAndFallback(models = MODEL_LIST) { model ->
            callSingleModelAttempt(
                model = model,
                apiKey = apiKey,
                prompt = prompt,
                jsonMode = jsonMode
            )
        }
    }

    private fun callSingleModelAttempt(
        model: String,
        apiKey: String,
        prompt: String,
        jsonMode: Boolean
    ): SingleAttemptResult {
        var connection: HttpURLConnection? = null
        return try {
            val endpoint = "$GEMINI_API_BASE/$model:generateContent"
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            val requestBody = buildRequestJson(prompt = prompt, jsonMode = jsonMode)
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            when (val statusCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val rawBody = readBoundedStream(connection.inputStream)
                    val extracted = extractCandidateText(rawBody)
                    if (extracted.isNullOrBlank()) {
                        SingleAttemptResult.NonRetryableFailure(
                            "Respons Gemini kosong. Coba beberapa saat lagi."
                        )
                    } else {
                        SingleAttemptResult.Success(extracted)
                    }
                }
                HttpURLConnection.HTTP_UNAVAILABLE -> {
                    SingleAttemptResult.RetryableFailure(
                        statusCode = HttpURLConnection.HTTP_UNAVAILABLE,
                        userMessage = OVERLOADED_FRIENDLY_MESSAGE
                    )
                }
                429 -> {
                    SingleAttemptResult.RetryableFailure(
                        statusCode = 429,
                        userMessage = "Kuota Gemini sementara penuh. Coba tekan Refresh sebentar lagi ya."
                    )
                }
                HttpURLConnection.HTTP_BAD_REQUEST,
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    SingleAttemptResult.NonRetryableFailure(
                        "API Key Gemini tidak valid atau belum aktif. Periksa kembali di Pengaturan."
                    )
                }
                else -> {
                    SingleAttemptResult.NonRetryableFailure(
                        "Layanan Gemini sedang tidak merespons (kode $statusCode). Coba lagi nanti."
                    )
                }
            }
        } catch (_: Exception) {
            SingleAttemptResult.NonRetryableFailure(
                "Tidak dapat terhubung ke Gemini AI. Periksa koneksi internetmu."
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildRequestJson(prompt: String, jsonMode: Boolean): String {
        val partObj = JSONObject().put("text", prompt)
        val contentObj = JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(partObj))

        val genConfig = JSONObject().put("temperature", 0.7)
        if (jsonMode) {
            genConfig.put("responseMimeType", "application/json")
        }

        return JSONObject()
            .put("contents", JSONArray().put(contentObj))
            .put("generationConfig", genConfig)
            .toString()
    }

    private fun extractCandidateText(rawResponse: String): String? {
        return try {
            val root = JSONObject(rawResponse)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (!part.isNull("text")) {
                    sb.append(part.optString("text", ""))
                }
            }
            sb.toString().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    internal fun readBoundedStream(stream: java.io.InputStream): String {
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            val buffer = CharArray(4096)
            var total = 0
            while (true) {
                val read = reader.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_RESPONSE_CHARS) break
                sb.append(buffer, 0, read)
            }
        }
        return sb.toString()
    }
}
