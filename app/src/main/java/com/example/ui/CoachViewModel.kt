package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AiCoachService
import com.example.AiProvider
import com.example.DailySuggestionsResult
import com.example.ReminderScheduler
import com.example.WeeklyReviewResult
import com.example.data.FirebaseRepository
import com.example.data.GeminiApiKeyStore
import com.example.data.ReminderSettings
import com.example.data.WorkoutRepository
import com.example.domain.CoachingEngine
import com.example.domain.DateTimeHelper
import com.example.domain.GreetingPeriod
import com.example.health.HealthConnectAvailability
import com.example.health.HealthConnectManager
import com.example.model.ActiveWorkoutState
import com.example.model.DailyRecommendation
import com.example.model.DumbbellItem
import com.example.model.SessionSource
import com.example.model.SportType
import com.example.model.SuggestionOption
import com.example.model.TodayStatus
import com.example.model.WeeklyRhythmSummary
import com.example.model.WorkoutSession
import com.example.ui.theme.AppThemeMode
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class CloudSyncStatus {
    LOADING,
    SYNCED,
    SYNCING,
    ERROR
}

sealed interface WeeklyReviewUiState {
    data object Idle : WeeklyReviewUiState
    data object Loading : WeeklyReviewUiState
    data object MissingApiKey : WeeklyReviewUiState
    data class Success(val reviewText: String) : WeeklyReviewUiState
    data class Error(val message: String) : WeeklyReviewUiState
}

private sealed interface CloudSessionsStreamState {
    data object Loading : CloudSessionsStreamState
    data class Success(val sessions: List<WorkoutSession>) : CloudSessionsStreamState
    data class Error(val message: String) : CloudSessionsStreamState
}

data class CoachUiState(
    val greetingPeriod: GreetingPeriod = DateTimeHelper.getGreetingPeriod(),
    val todayStatus: TodayStatus = TodayStatus.FRESH,
    val allSessions: List<WorkoutSession> = emptyList(),
    val recentSessions: List<WorkoutSession> = emptyList(),
    val weeklyRhythm: WeeklyRhythmSummary = CoachingEngine.computeWeeklyRhythm(emptyList()),
    val recommendation: DailyRecommendation = CoachingEngine.generateDailyRecommendation(
        sessions = emptyList(),
        todayStatus = TodayStatus.FRESH
    ),
    val themeMode: AppThemeMode = AppThemeMode.WARM_CREAM,
    val reminderSettings: ReminderSettings = ReminderSettings(),
    val userDisplayName: String? = null,
    val userEmail: String? = null,
    val isCloudSyncEnabled: Boolean = false,
    val cloudSyncStatus: CloudSyncStatus = CloudSyncStatus.LOADING,
    val cloudErrorMessage: String? = null,
    val isSyncingHealthConnect: Boolean = false,
    val healthConnectStatusMessage: String? = null,
    val snackbarMessage: String? = null,
    val activeWorkout: ActiveWorkoutState? = null,
    val aiProvider: AiProvider = AiProvider.GROQ,
    val activeAiApiKey: String = "",
    val isLoadingAiSuggestions: Boolean = false,
    val weeklyReviewState: WeeklyReviewUiState = WeeklyReviewUiState.Idle
) {
    val hasAiApiKey: Boolean
        get() = activeAiApiKey.isNotBlank()
}

class CoachViewModel(
    private val currentUserId: String?,
    private val userDisplayName: String?,
    private val userEmail: String?,
    private val localRepository: WorkoutRepository,
    private val firebaseRepository: FirebaseRepository?,
    val healthConnectManager: HealthConnectManager,
    private val reminderScheduler: ReminderScheduler,
    private val geminiApiKeyStore: GeminiApiKeyStore
) : ViewModel() {

    private val isCloudMode: Boolean = firebaseRepository != null && !currentUserId.isNullOrBlank()

    private val selectedStatus = MutableStateFlow(TodayStatus.FRESH)
    private val isSyncingCloud = MutableStateFlow(false)
    private val isSyncingHealthConnect = MutableStateFlow(false)
    private val healthConnectStatusMessage = MutableStateFlow<String?>(null)
    private val snackbarMessage = MutableStateFlow<String?>(null)
    private val activeWorkout = MutableStateFlow<ActiveWorkoutState?>(null)
    private val selectedAiProvider = MutableStateFlow(geminiApiKeyStore.getSelectedProvider())
    private val activeAiApiKey = MutableStateFlow(geminiApiKeyStore.getApiKey(selectedAiProvider.value))
    private val aiRecommendationsCache = MutableStateFlow<Map<TodayStatus, DailyRecommendation>>(emptyMap())
    private val isLoadingAiSuggestions = MutableStateFlow(false)
    private val weeklyReviewState = MutableStateFlow<WeeklyReviewUiState>(WeeklyReviewUiState.Idle)

    // In Cloud Mode: observes Firestore with two-tier Flow error handling.
    // In Local Mode (when google-services.json is absent): observes DataStore sessions directly.
    private val cloudSessionsState: StateFlow<CloudSessionsStreamState> =
        if (firebaseRepository != null && !currentUserId.isNullOrBlank()) {
            firebaseRepository.observeWorkoutSessions(currentUserId)
                .map<List<WorkoutSession>, CloudSessionsStreamState> { sessions ->
                    CloudSessionsStreamState.Success(sessions)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing cloud workouts", rootError)
                    emit(
                        CloudSessionsStreamState.Error(
                            rootError.localizedMessage
                                ?: "Gagal memuat data cloud. Periksa koneksi internetmu."
                        )
                    )
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = CloudSessionsStreamState.Loading
                )
        } else {
            localRepository.sessionsFlow
                .map<List<WorkoutSession>, CloudSessionsStreamState> { sessions ->
                    CloudSessionsStreamState.Success(sessions)
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Error observing local workouts", error)
                    emit(CloudSessionsStreamState.Success(emptyList()))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = CloudSessionsStreamState.Success(emptyList())
                )
        }

    private val baseFlow = combine(
        cloudSessionsState,
        selectedStatus,
        localRepository.themeModeFlow
    ) { streamState, status, themeMode ->
        Triple(streamState, status, themeMode)
    }

    private val syncFlagsFlow = combine(
        isSyncingCloud,
        isSyncingHealthConnect,
        healthConnectStatusMessage
    ) { cloudSyncing, hcSyncing, hcMessage ->
        Triple(cloudSyncing, hcSyncing, hcMessage)
    }

    private data class AiUiSnapshot(
        val provider: AiProvider,
        val apiKey: String,
        val cachedMap: Map<TodayStatus, DailyRecommendation>,
        val isLoadingSuggestions: Boolean,
        val weeklyReview: WeeklyReviewUiState
    )

    private val aiStateFlow = combine(
        selectedAiProvider,
        activeAiApiKey,
        aiRecommendationsCache,
        isLoadingAiSuggestions,
        weeklyReviewState
    ) { provider, key, cache, loadingAi, review ->
        AiUiSnapshot(provider, key, cache, loadingAi, review)
    }

    private val overlayFlow = combine(
        snackbarMessage,
        activeWorkout,
        aiStateFlow
    ) { message, liveWorkout, aiSnapshot ->
        Triple(message, liveWorkout, aiSnapshot)
    }

    val uiState: StateFlow<CoachUiState> = combine(
        baseFlow,
        localRepository.reminderSettingsFlow,
        syncFlagsFlow,
        overlayFlow
    ) { (streamState, status, themeMode), reminderSettings, (cloudSyncing, hcSyncing, hcStatus), (message, liveWorkout, aiSnapshot) ->
        val nowMillis = System.currentTimeMillis()
        val sessions = when (streamState) {
            is CloudSessionsStreamState.Success -> streamState.sessions
            else -> emptyList()
        }
        val cloudStatus = when {
            cloudSyncing -> CloudSyncStatus.SYNCING
            streamState is CloudSessionsStreamState.Loading -> CloudSyncStatus.LOADING
            streamState is CloudSessionsStreamState.Error -> CloudSyncStatus.ERROR
            else -> CloudSyncStatus.SYNCED
        }
        val cloudError = (streamState as? CloudSessionsStreamState.Error)?.message

        val localRec = CoachingEngine.generateDailyRecommendation(sessions, status, nowMillis)
        val effectiveRecommendation = if (aiSnapshot.apiKey.isNotBlank()) {
            aiSnapshot.cachedMap[status] ?: localRec
        } else {
            localRec
        }

        CoachUiState(
            greetingPeriod = DateTimeHelper.getGreetingPeriod(nowMillis),
            todayStatus = status,
            allSessions = sessions,
            recentSessions = sessions.take(5),
            weeklyRhythm = CoachingEngine.computeWeeklyRhythm(sessions, nowMillis),
            recommendation = effectiveRecommendation,
            themeMode = themeMode,
            reminderSettings = reminderSettings,
            userDisplayName = userDisplayName,
            userEmail = userEmail,
            isCloudSyncEnabled = isCloudMode,
            cloudSyncStatus = cloudStatus,
            cloudErrorMessage = cloudError,
            isSyncingHealthConnect = hcSyncing,
            healthConnectStatusMessage = hcStatus,
            snackbarMessage = message,
            activeWorkout = liveWorkout,
            aiProvider = aiSnapshot.provider,
            activeAiApiKey = aiSnapshot.apiKey,
            isLoadingAiSuggestions = aiSnapshot.isLoadingSuggestions,
            weeklyReviewState = aiSnapshot.weeklyReview
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CoachUiState(
            userDisplayName = userDisplayName,
            userEmail = userEmail,
            isCloudSyncEnabled = isCloudMode,
            cloudSyncStatus = if (isCloudMode) CloudSyncStatus.LOADING else CloudSyncStatus.SYNCED,
            aiProvider = selectedAiProvider.value,
            activeAiApiKey = activeAiApiKey.value
        )
    )

    init {
        if (isCloudMode) {
            initializeCloudAccountAndMigrate()
            observeCloudThemePreference()
        }
    }

    private fun initializeCloudAccountAndMigrate() {
        val repo = firebaseRepository ?: return
        val uid = currentUserId?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            val currentLocalTheme = localRepository.themeModeFlow.first()
            repo.ensureUserProfile(
                userId = uid,
                displayName = userDisplayName,
                email = userEmail,
                fallbackThemeMode = currentLocalTheme
            )

            // Automatically migrate any legacy local-only workouts recorded prior to cloud sign-in
            val legacyLocalSessions = localRepository.sessionsFlow.first()
            if (legacyLocalSessions.isNotEmpty()) {
                isSyncingCloud.value = true
                val existingCloud = uiState.value.allSessions
                val migrationResult = repo.upsertSyncedSessions(
                    userId = uid,
                    incomingSessions = legacyLocalSessions,
                    existingSessions = existingCloud
                )
                migrationResult.onSuccess { uploadedCount ->
                    localRepository.clearAllSessions()
                    if (uploadedCount > 0) {
                        snackbarMessage.value =
                            "$uploadedCount catatan latihan lokal berhasil disinkronkan ke cloud!"
                    }
                }
                isSyncingCloud.value = false
            }
        }
    }

    private fun observeCloudThemePreference() {
        val repo = firebaseRepository ?: return
        val uid = currentUserId?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            repo.observeUserThemeMode(uid)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing cloud theme preference", firestoreError ?: error)
                }
                .collect { cloudTheme ->
                    if (cloudTheme != null) {
                        val currentLocal = localRepository.themeModeFlow.first()
                        if (currentLocal != cloudTheme) {
                            localRepository.setThemeMode(cloudTheme)
                        }
                    }
                }
        }
    }

    fun selectTodayStatus(status: TodayStatus) {
        selectedStatus.value = status
        // Auto-fetch AI suggestion once per status if API Key is configured and not yet cached
        if (activeAiApiKey.value.isNotBlank() && aiRecommendationsCache.value[status] == null) {
            refreshAiSuggestions(targetStatus = status, explicitTrigger = false)
        }
    }

    fun selectAiProvider(provider: AiProvider) {
        if (selectedAiProvider.value == provider) return
        geminiApiKeyStore.saveSelectedProvider(provider)
        selectedAiProvider.value = provider
        val storedKey = geminiApiKeyStore.getApiKey(provider)
        activeAiApiKey.value = storedKey
        aiRecommendationsCache.value = emptyMap()
        if (storedKey.isNotBlank()) {
            snackbarMessage.value = "Penyedia AI dialihkan ke ${provider.displayName} ✨"
            refreshAiSuggestions(targetStatus = selectedStatus.value, explicitTrigger = false)
        } else {
            snackbarMessage.value = "Penyedia AI: ${provider.displayName}. Masukkan API Key untuk mengaktifkan."
        }
    }

    fun saveAiApiKey(provider: AiProvider, rawKey: String) {
        val cleaned = rawKey.trim()
        geminiApiKeyStore.saveSelectedProvider(provider)
        geminiApiKeyStore.saveApiKey(provider, cleaned)
        selectedAiProvider.value = provider
        activeAiApiKey.value = cleaned
        aiRecommendationsCache.value = emptyMap()
        if (cleaned.isNotEmpty()) {
            snackbarMessage.value = "API Key ${provider.displayName} tersimpan — AI Aktif ✨"
            refreshAiSuggestions(targetStatus = selectedStatus.value, explicitTrigger = false)
        } else {
            snackbarMessage.value = "API Key ${provider.displayName} dihapus — kembali ke Mode Offline (Aturan Lokal)."
        }
    }

    fun refreshAiSuggestions(
        targetStatus: TodayStatus = selectedStatus.value,
        explicitTrigger: Boolean = true
    ) {
        val provider = selectedAiProvider.value
        val key = activeAiApiKey.value.trim()
        if (key.isEmpty()) {
            if (explicitTrigger) {
                snackbarMessage.value = "Isi API Key ${provider.displayName} di Pengaturan terlebih dahulu."
            }
            return
        }
        viewModelScope.launch {
            isLoadingAiSuggestions.value = true
            try {
                when (
                    val result = AiCoachService.generateDailySuggestionsDetailed(
                        provider = provider,
                        apiKey = key,
                        sessions = uiState.value.allSessions,
                        energyState = targetStatus
                    )
                ) {
                    is DailySuggestionsResult.Success -> {
                        aiRecommendationsCache.value =
                            aiRecommendationsCache.value + (targetStatus to result.recommendation)
                    }
                    is DailySuggestionsResult.FallbackToLocal -> {
                        // Local CoachingEngine suggestions remain visible in UI so screen is never empty
                        if (explicitTrigger || result.isOverloaded) {
                            snackbarMessage.value = result.friendlyMessage
                        }
                    }
                }
            } finally {
                isLoadingAiSuggestions.value = false
            }
        }
    }

    fun requestWeeklyAiReview() {
        val provider = selectedAiProvider.value
        val key = activeAiApiKey.value.trim()
        if (key.isEmpty()) {
            weeklyReviewState.value = WeeklyReviewUiState.MissingApiKey
            return
        }
        viewModelScope.launch {
            weeklyReviewState.value = WeeklyReviewUiState.Loading
            when (
                val result = AiCoachService.generateWeeklyReview(
                    provider = provider,
                    apiKey = key,
                    sessions = uiState.value.allSessions
                )
            ) {
                is WeeklyReviewResult.Success -> {
                    weeklyReviewState.value = WeeklyReviewUiState.Success(result.reviewText)
                }
                is WeeklyReviewResult.Error -> {
                    weeklyReviewState.value = WeeklyReviewUiState.Error(result.message)
                }
            }
        }
    }

    fun dismissWeeklyAiReview() {
        weeklyReviewState.value = WeeklyReviewUiState.Idle
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            localRepository.setThemeMode(mode)
            val repo = firebaseRepository
            val uid = currentUserId
            if (repo != null && !uid.isNullOrBlank()) {
                repo.updateThemeMode(
                    userId = uid,
                    mode = mode,
                    displayName = userDisplayName,
                    email = userEmail
                )
            }
        }
    }

    fun setDailyReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.reminderSettings
            val updated = current.copy(enabled = enabled)
            localRepository.saveReminderSettings(updated)
            if (enabled) {
                reminderScheduler.scheduleDailyReminder(updated.hour, updated.minute)
                val formatted = formatTime(updated.hour, updated.minute)
                snackbarMessage.value = "Pengingat harian aktif setiap pukul $formatted."
            } else {
                reminderScheduler.cancelDailyReminder()
                snackbarMessage.value = "Pengingat harian dimatikan."
            }
        }
    }

    fun setDailyReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val current = uiState.value.reminderSettings
            val updated = current.copy(
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59)
            )
            localRepository.saveReminderSettings(updated)
            val formatted = formatTime(updated.hour, updated.minute)
            if (updated.enabled) {
                reminderScheduler.scheduleDailyReminder(updated.hour, updated.minute)
                snackbarMessage.value = "Jadwal pengingat harian diperbarui ke pukul $formatted."
            } else {
                snackbarMessage.value = "Jam pengingat diatur ke $formatted (aktifkan switch untuk menjadwalkan)."
            }
        }
    }

    fun triggerTestNotification() {
        reminderScheduler.triggerTestNotificationIn5Seconds()
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    fun syncCloudNow() {
        val repo = firebaseRepository
        val uid = currentUserId
        if (repo == null || uid.isNullOrBlank()) {
            snackbarMessage.value = "Aplikasi berjalan dalam Mode Lokal. Data tersimpan aman di perangkat ini."
            return
        }
        viewModelScope.launch {
            isSyncingCloud.value = true
            val result = repo.forceRefreshFromServer(uid)
            result.fold(
                onSuccess = { count ->
                    snackbarMessage.value =
                        "Sinkronisasi cloud berhasil ($count sesi tersimpan di akunmu)."
                },
                onFailure = { err ->
                    snackbarMessage.value =
                        err.localizedMessage ?: "Gagal terhubung ke server cloud. Coba lagi saat online."
                }
            )
            isSyncingCloud.value = false
        }
    }

    fun checkHealthConnectAvailability(): HealthConnectAvailability {
        return healthConnectManager.checkAvailability()
    }

    fun triggerHealthConnectSync(onPermissionsNeeded: (Set<String>) -> Unit) {
        viewModelScope.launch {
            when (healthConnectManager.checkAvailability()) {
                HealthConnectAvailability.UNAVAILABLE -> {
                    val msg = "Health Connect tidak tersedia di perangkat ini."
                    healthConnectStatusMessage.value = msg
                    snackbarMessage.value = msg
                    return@launch
                }
                HealthConnectAvailability.UPDATE_REQUIRED -> {
                    val msg = "Perlu instalasi atau pembaruan aplikasi Health Connect di Play Store."
                    healthConnectStatusMessage.value = msg
                    snackbarMessage.value = msg
                    return@launch
                }
                HealthConnectAvailability.AVAILABLE -> Unit
            }

            if (healthConnectManager.hasAllPermissions()) {
                performHealthConnectSync()
            } else {
                onPermissionsNeeded(healthConnectManager.requiredPermissions)
            }
        }
    }

    fun onHealthConnectPermissionsResult(grantedPermissions: Set<String>) {
        if (grantedPermissions.containsAll(healthConnectManager.requiredPermissions)) {
            performHealthConnectSync()
        } else {
            val msg = "Izin Health Connect belum lengkap. Izinkan baca Olahraga, Jarak, dan Detak Jantung untuk sync."
            healthConnectStatusMessage.value = msg
            snackbarMessage.value = msg
        }
    }

    private fun performHealthConnectSync() {
        viewModelScope.launch {
            isSyncingHealthConnect.value = true
            healthConnectStatusMessage.value = "Menarik sesi latihan 14 hari terakhir..."
            val syncResult = healthConnectManager.syncLast14DaysWorkouts()
            syncResult.fold(
                onSuccess = { syncedSessions ->
                    val repo = firebaseRepository
                    val uid = currentUserId
                    if (repo != null && !uid.isNullOrBlank()) {
                        val upsertResult = repo.upsertSyncedSessions(
                            userId = uid,
                            incomingSessions = syncedSessions,
                            existingSessions = uiState.value.allSessions
                        )
                        upsertResult.fold(
                            onSuccess = { newCount ->
                                val msg = when {
                                    syncedSessions.isEmpty() ->
                                        "Terhubung! Tidak ditemukan sesi olahraga di Health Connect dalam 14 hari terakhir."
                                    newCount > 0 ->
                                        "Berhasil sync $newCount sesi baru dari Health Connect ke Cloud!"
                                    else ->
                                        "Data sudah terbaru (${syncedSessions.size} sesi 14 hari terakhir sudah tersimpan di Cloud)."
                                }
                                healthConnectStatusMessage.value = msg
                                snackbarMessage.value = msg
                            },
                            onFailure = { err ->
                                val msg = err.localizedMessage
                                    ?: "Gagal menyimpan hasil Health Connect ke cloud."
                                healthConnectStatusMessage.value = msg
                                snackbarMessage.value = msg
                            }
                        )
                    } else {
                        val newCount = localRepository.upsertSyncedSessions(syncedSessions)
                        val msg = when {
                            syncedSessions.isEmpty() ->
                                "Terhubung! Tidak ditemukan sesi olahraga di Health Connect dalam 14 hari terakhir."
                            newCount > 0 ->
                                "Berhasil sync $newCount sesi baru dari Health Connect ke perangkat!"
                            else ->
                                "Data sudah terbaru (${syncedSessions.size} sesi 14 hari terakhir sudah tersimpan)."
                        }
                        healthConnectStatusMessage.value = msg
                        snackbarMessage.value = msg
                    }
                },
                onFailure = { err ->
                    val msg = err.message ?: "Gagal menyinkronkan data Health Connect."
                    healthConnectStatusMessage.value = msg
                    snackbarMessage.value = msg
                }
            )
            isSyncingHealthConnect.value = false
        }
    }

    fun completeSuggestion(option: SuggestionOption, isIdeal: Boolean) {
        viewModelScope.launch {
            val session = WorkoutSession(
                id = UUID.randomUUID().toString(),
                externalId = null,
                sportType = option.sportType,
                source = SessionSource.MANUAL,
                startedAtIso = DateTimeHelper.formatNowIso(),
                durationMin = option.estimatedDurationMin.coerceIn(1, 1440),
                distanceKm = null,
                avgHr = null,
                rpe = option.defaultRpe.coerceIn(1, 10),
                notes = "${option.badge}: ${option.title}",
                dumbbellItems = option.suggestedDumbbellItems
            )
            val repo = firebaseRepository
            val uid = currentUserId
            if (repo != null && !uid.isNullOrBlank()) {
                repo.createWorkoutSession(uid, session).fold(
                    onSuccess = {
                        snackbarMessage.value = if (isIdeal) {
                            "Mantap! Sesi ${option.sportType.displayName} tersimpan ke cloud!"
                        } else {
                            "Keren banget! Anti-mager berhasil — langkah ringan hari ini tersimpan ke cloud."
                        }
                    },
                    onFailure = {
                        snackbarMessage.value = "Gagal menyimpan sesi ke cloud. Coba lagi sebentar lagi."
                    }
                )
            } else {
                localRepository.addSession(session)
                snackbarMessage.value = if (isIdeal) {
                    "Mantap! Sesi ${option.sportType.displayName} tersimpan!"
                } else {
                    "Keren banget! Anti-mager berhasil — langkah ringan hari ini tercatat."
                }
            }
        }
    }

    fun startWorkoutFromSuggestion(
        option: SuggestionOption,
        isIdeal: Boolean,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        activeWorkout.value = ActiveWorkoutState(
            title = option.title,
            sportType = option.sportType,
            source = SessionSource.MANUAL,
            targetDurationMin = option.estimatedDurationMin.takeIf { it > 0 },
            targetDurationLabel = option.durationLabel,
            isFromIdealSuggestion = isIdeal,
            defaultRpe = option.defaultRpe.coerceIn(1, 10),
            initialNotes = "${option.badge}: ${option.title}",
            dumbbellItems = option.suggestedDumbbellItems,
            startedAtEpochMs = nowEpochMs
        )
    }

    fun startWorkoutFromManual(
        sportType: SportType,
        source: SessionSource,
        targetDurationMin: Int?,
        distanceKm: Double?,
        avgHr: Int?,
        rpe: Int,
        notes: String?,
        dumbbellItems: List<DumbbellItem>,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        val cleanItems = if (sportType == SportType.DUMBBELL) dumbbellItems.take(30) else emptyList()
        val sessionTitle = when {
            sportType == SportType.DUMBBELL && cleanItems.isNotEmpty() ->
                "Latihan ${sportType.displayName} (${cleanItems.size} Gerakan)"
            else -> "Sesi ${sportType.displayName}"
        }
        activeWorkout.value = ActiveWorkoutState(
            title = sessionTitle,
            sportType = sportType,
            source = source,
            targetDurationMin = targetDurationMin?.takeIf { it > 0 },
            targetDurationLabel = targetDurationMin?.takeIf { it > 0 }?.let { "$it mnt" },
            isFromIdealSuggestion = null,
            defaultRpe = rpe.coerceIn(1, 10),
            initialDistanceKm = distanceKm?.takeIf { it > 0.0 },
            initialAvgHr = avgHr?.takeIf { it in 30..240 },
            initialNotes = notes?.trim()?.takeIf { it.isNotEmpty() },
            dumbbellItems = cleanItems,
            startedAtEpochMs = nowEpochMs
        )
    }

    fun togglePauseActiveWorkout(nowEpochMs: Long = System.currentTimeMillis()) {
        val current = activeWorkout.value ?: return
        activeWorkout.value = current.togglePause(nowEpochMs)
    }

    fun pauseActiveWorkoutForFinish(nowEpochMs: Long = System.currentTimeMillis()) {
        val current = activeWorkout.value ?: return
        activeWorkout.value = current.pause(nowEpochMs)
    }

    fun resumeActiveWorkoutIfPaused(nowEpochMs: Long = System.currentTimeMillis()) {
        val current = activeWorkout.value ?: return
        activeWorkout.value = current.resume(nowEpochMs)
    }

    fun toggleActiveExerciseCompleted(index: Int) {
        val current = activeWorkout.value ?: return
        activeWorkout.value = current.toggleExerciseCompleted(index)
    }

    fun cancelActiveWorkout() {
        activeWorkout.value = null
    }

    fun finishActiveWorkout(
        durationMin: Int,
        rpe: Int,
        distanceKm: Double?,
        notes: String?
    ) {
        val current = activeWorkout.value ?: return
        val safeDuration = durationMin.coerceIn(1, 1440)
        val session = WorkoutSession(
            id = UUID.randomUUID().toString(),
            externalId = null,
            sportType = current.sportType,
            source = current.source,
            startedAtIso = DateTimeHelper.formatNowIso(current.startedAtEpochMs),
            durationMin = safeDuration,
            distanceKm = distanceKm?.takeIf { it > 0.0 && it <= 500.0 },
            avgHr = current.initialAvgHr?.takeIf { it in 30..240 },
            rpe = rpe.coerceIn(1, 10),
            notes = notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
            dumbbellItems = if (current.sportType == SportType.DUMBBELL) {
                current.dumbbellItems.take(30)
            } else {
                emptyList()
            }
        )
        activeWorkout.value = null
        viewModelScope.launch {
            val repo = firebaseRepository
            val uid = currentUserId
            if (repo != null && !uid.isNullOrBlank()) {
                repo.createWorkoutSession(uid, session).fold(
                    onSuccess = {
                        snackbarMessage.value = when (current.isFromIdealSuggestion) {
                            true -> "Mantap! Sesi ${current.sportType.displayName} ($safeDuration mnt) tercatat real-time ke cloud!"
                            false -> "Keren banget! Langkah anti-mager ($safeDuration mnt) tersimpan ke cloud!"
                            null -> "Hebat! Sesi ${current.sportType.displayName} ($safeDuration mnt) berhasil disimpan!"
                        }
                    },
                    onFailure = {
                        snackbarMessage.value = "Gagal menyimpan sesi ke cloud. Silakan coba lagi sebentar lagi."
                    }
                )
            } else {
                localRepository.addSession(session)
                snackbarMessage.value = when (current.isFromIdealSuggestion) {
                    true -> "Mantap! Sesi ${current.sportType.displayName} ($safeDuration mnt) tercatat real-time!"
                    false -> "Keren banget! Langkah anti-mager ($safeDuration mnt) tersimpan!"
                    null -> "Hebat! Sesi ${current.sportType.displayName} ($safeDuration mnt) berhasil disimpan!"
                }
            }
        }
    }

    fun saveManualSession(
        sportType: SportType,
        source: SessionSource,
        durationMin: Int,
        distanceKm: Double?,
        avgHr: Int?,
        rpe: Int,
        notes: String?,
        dumbbellItems: List<DumbbellItem>
    ) {
        viewModelScope.launch {
            val session = WorkoutSession(
                id = UUID.randomUUID().toString(),
                externalId = null,
                sportType = sportType,
                source = source,
                startedAtIso = DateTimeHelper.formatNowIso(),
                durationMin = durationMin.coerceIn(1, 1440),
                distanceKm = distanceKm?.takeIf { it > 0.0 && it <= 500.0 },
                avgHr = avgHr?.takeIf { it in 30..240 },
                rpe = rpe.coerceIn(1, 10),
                notes = notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
                dumbbellItems = if (sportType == SportType.DUMBBELL) {
                    dumbbellItems.take(30)
                } else {
                    emptyList()
                }
            )
            val repo = firebaseRepository
            val uid = currentUserId
            if (repo != null && !uid.isNullOrBlank()) {
                repo.createWorkoutSession(uid, session).fold(
                    onSuccess = {
                        snackbarMessage.value =
                            "Sesi ${sportType.displayName} (${session.durationMin} menit) tersimpan di cloud!"
                    },
                    onFailure = {
                        snackbarMessage.value = "Gagal menyimpan latihan ke cloud. Silakan coba lagi."
                    }
                )
            } else {
                localRepository.addSession(session)
                snackbarMessage.value =
                    "Sesi ${sportType.displayName} (${session.durationMin} menit) berhasil disimpan!"
            }
        }
    }

    fun updateSession(
        existing: WorkoutSession,
        sportType: SportType,
        durationMin: Int,
        distanceKm: Double?,
        avgHr: Int?,
        rpe: Int,
        notes: String?,
        dumbbellItems: List<DumbbellItem>
    ) {
        viewModelScope.launch {
            val updated = existing.copy(
                sportType = sportType,
                durationMin = durationMin.coerceIn(1, 1440),
                distanceKm = distanceKm?.takeIf { it > 0.0 && it <= 500.0 },
                avgHr = avgHr?.takeIf { it in 30..240 },
                rpe = rpe.coerceIn(1, 10),
                notes = notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
                dumbbellItems = if (sportType == SportType.DUMBBELL) {
                    dumbbellItems.take(30)
                } else {
                    emptyList()
                }
            )
            val repo = firebaseRepository
            val uid = currentUserId
            if (repo != null && !uid.isNullOrBlank()) {
                repo.updateWorkoutSession(uid, updated).fold(
                    onSuccess = {
                        snackbarMessage.value =
                            "Perubahan sesi ${sportType.displayName} berhasil disimpan ke cloud."
                    },
                    onFailure = {
                        snackbarMessage.value = "Gagal memperbarui sesi di cloud. Silakan coba lagi."
                    }
                )
            } else {
                localRepository.updateSession(updated)
                snackbarMessage.value =
                    "Perubahan sesi ${sportType.displayName} berhasil disimpan."
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val repo = firebaseRepository
            val uid = currentUserId
            if (repo != null && !uid.isNullOrBlank()) {
                repo.deleteWorkoutSession(uid, sessionId).fold(
                    onSuccess = {
                        snackbarMessage.value = "Catatan sesi berhasil dihapus dari cloud."
                    },
                    onFailure = {
                        snackbarMessage.value = "Gagal menghapus catatan sesi. Silakan coba lagi."
                    }
                )
            } else {
                localRepository.deleteSession(sessionId)
                snackbarMessage.value = "Catatan sesi berhasil dihapus."
            }
        }
    }

    fun importSessionsJson(rawJson: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val parsed = localRepository.parseSessionsJson(rawJson)
            parsed.fold(
                onSuccess = { importedList ->
                    val repo = firebaseRepository
                    val uid = currentUserId
                    if (repo != null && !uid.isNullOrBlank()) {
                        val upsertResult = repo.upsertSyncedSessions(
                            userId = uid,
                            incomingSessions = importedList,
                            existingSessions = uiState.value.allSessions
                        )
                        upsertResult.fold(
                            onSuccess = { count ->
                                val msg = if (count > 0) {
                                    "Berhasil mengimpor $count sesi latihan baru ke cloud."
                                } else {
                                    "Tidak ada sesi baru ditambahkan (semua data sudah ada di cloud)."
                                }
                                snackbarMessage.value = msg
                                onResult(true, msg)
                            },
                            onFailure = { err ->
                                onResult(
                                    false,
                                    err.localizedMessage ?: "Gagal mengunggah data JSON ke cloud."
                                )
                            }
                        )
                    } else {
                        val count = localRepository.upsertSyncedSessions(importedList)
                        val msg = if (count > 0) {
                            "Berhasil mengimpor $count sesi latihan baru ke perangkat."
                        } else {
                            "Tidak ada sesi baru ditambahkan (semua data sudah ada)."
                        }
                        snackbarMessage.value = msg
                        onResult(true, msg)
                    }
                },
                onFailure = { error ->
                    onResult(false, error.message ?: "Gagal mengimpor JSON.")
                }
            )
        }
    }

    fun exportSessionsJson(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = localRepository.serializeSessionsJson(uiState.value.allSessions)
            onExportReady(json)
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            val repo = firebaseRepository
            val uid = currentUserId
            if (repo != null && !uid.isNullOrBlank()) {
                repo.clearAllWorkoutSessions(uid).fold(
                    onSuccess = {
                        localRepository.clearAllSessions()
                        snackbarMessage.value = "Semua riwayat latihan di cloud telah dibersihkan."
                    },
                    onFailure = {
                        snackbarMessage.value = "Gagal membersihkan riwayat cloud. Silakan coba lagi."
                    }
                )
            } else {
                localRepository.clearAllSessions()
                snackbarMessage.value = "Semua riwayat latihan lokal telah dibersihkan."
            }
        }
    }

    fun clearSnackbar() {
        snackbarMessage.value = null
    }

    private companion object {
        const val TAG = "CoachViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
