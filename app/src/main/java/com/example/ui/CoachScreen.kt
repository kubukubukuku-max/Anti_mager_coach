package com.example.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.NotificationHelper
import com.example.R
import com.example.data.ExerciseGuide
import com.example.data.ExerciseGuides
import com.example.domain.GreetingPeriod
import com.example.model.TodayStatus
import com.example.model.WorkoutSession
import com.example.ui.components.ActiveWorkoutScreen
import com.example.ui.components.EmptyHistoryCard
import com.example.ui.components.ExerciseGuideDialog
import com.example.ui.components.HistorySessionCard
import com.example.ui.components.LogWorkoutSheet
import com.example.ui.components.SettingsSyncBottomSheet
import com.example.ui.components.SuggestionCard
import com.example.ui.components.WeeklyAiReviewDialog
import com.example.ui.components.WeeklyRhythmCard
import com.example.ui.components.toIcon
import com.example.ui.theme.AntiMagerTheme
import kotlinx.coroutines.launch

@Composable
fun CoachScreen(
    viewModel: CoachViewModel,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val extendedColors = AntiMagerTheme.extendedColors
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.healthConnectManager.createPermissionRequestContract()
    ) { grantedPermissions ->
        viewModel.onHealthConnectPermissionsResult(grantedPermissions)
    }

    var pendingNotificationAction by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingNotificationAction
        pendingNotificationAction = null
        if (granted) {
            when (action) {
                "enable_reminder" -> viewModel.setDailyReminderEnabled(true)
                "test_notification" -> viewModel.triggerTestNotification()
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    "Izin notifikasi ditolak. Aktifkan izin notifikasi di pengaturan perangkat agar banner pengingat dapat muncul.",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    var showManualLogSheet by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<WorkoutSession?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var pendingDeleteSession by remember { mutableStateOf<WorkoutSession?>(null) }
    var activeSuggestionGuide by remember { mutableStateOf<ExerciseGuide?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSnackbar()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .testTag("coach_lazy_column"),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Header: Auto greeting, "Anti-Mager Coach", brief subtitle, and 1 Settings/Sync icon button at top-right
                item(key = "header") {
                    CoachHeader(
                        greetingPeriod = uiState.greetingPeriod,
                        onSettingsClick = { showSettingsSheet = true }
                    )
                }

                // Subtle Cloud Sync error banner only when Firestore reports an error
                if (uiState.cloudSyncStatus == CloudSyncStatus.ERROR && !uiState.cloudErrorMessage.isNullOrBlank()) {
                    item(key = "cloud_error_banner") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = extendedColors.lightContainer,
                            border = BorderStroke(1.dp, extendedColors.lightBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cloud_error_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uiState.cloudErrorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = extendedColors.lightAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.syncCloudNow() }) {
                                    Text(
                                        text = "Coba Lagi",
                                        color = extendedColors.lightAccent
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Today Status Chips (Lagi Fit, Agak Capek, Waktu Mepet)
                item(key = "today_status_chips") {
                    TodayStatusSection(
                        selectedStatus = uiState.todayStatus,
                        onSelectStatus = viewModel::selectTodayStatus
                    )
                }

                // 3. Weekly Rhythm Card & 7-Day Workout Consistency Chart (Firestore-backed)
                item(key = "weekly_rhythm") {
                    WeeklyRhythmCard(
                        summary = uiState.weeklyRhythm,
                        cloudSyncStatus = uiState.cloudSyncStatus,
                        onWeeklyAiReviewClick = { viewModel.requestWeeklyAiReview() }
                    )
                }

                // 4. Single centered "Catat Latihan Manual" card button right below weekly rhythm
                item(key = "manual_log_card") {
                    ManualLogEntryCard(
                        onClick = { showManualLogSheet = true }
                    )
                }

                // 5. Daily Suggestions Section Header + Contextual Insight Badge + Refresh AI + 2 Suggestion Cards
                item(key = "suggestions_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.section_daily_suggestions),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = uiState.recommendation.contextSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (uiState.recommendation.insightBadge.isNotBlank() || uiState.hasAiApiKey) {
                            val isHighlightWarm = uiState.recommendation.insights.isLowerBodyFatigued ||
                                uiState.recommendation.insights.readyForProgression ||
                                uiState.todayStatus == TodayStatus.TIRED
                            val badgeBg = if (isHighlightWarm) {
                                extendedColors.lightContainer
                            } else {
                                extendedColors.idealContainer
                            }
                            val badgeBorder = if (isHighlightWarm) {
                                extendedColors.lightBorder
                            } else {
                                extendedColors.idealBorder
                            }
                            val badgeTextColor = if (isHighlightWarm) {
                                extendedColors.lightAccent
                            } else {
                                extendedColors.idealAccent
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uiState.recommendation.insightBadge.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = badgeBg,
                                        border = BorderStroke(1.dp, badgeBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("coaching_insight_badge")
                                    ) {
                                        Text(
                                            text = uiState.recommendation.insightBadge,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = badgeTextColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                if (uiState.hasAiApiKey) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, extendedColors.idealBorder),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(enabled = !uiState.isLoadingAiSuggestions) {
                                                viewModel.refreshAiSuggestions(explicitTrigger = true)
                                            }
                                            .testTag("refresh_ai_suggestions_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (uiState.isLoadingAiSuggestions) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(15.dp),
                                                    strokeWidth = 2.dp,
                                                    color = extendedColors.idealAccent
                                                )
                                            }
                                            Text(
                                                text = stringResource(R.string.action_refresh_ai),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = extendedColors.idealAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.isLoadingAiSuggestions) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .testTag("ai_suggestions_loading_indicator")
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = extendedColors.idealAccent
                                )
                                Text(
                                    text = stringResource(R.string.ai_loading_suggestions),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item(key = "ideal_suggestion") {
                    SuggestionCard(
                        option = uiState.recommendation.idealOption,
                        isIdeal = true,
                        isAiGenerated = uiState.recommendation.isAiGenerated,
                        onStartWorkoutClick = {
                            viewModel.startWorkoutFromSuggestion(
                                option = uiState.recommendation.idealOption,
                                isIdeal = true
                            )
                        },
                        onCompleteClick = {
                            viewModel.completeSuggestion(
                                option = uiState.recommendation.idealOption,
                                isIdeal = true
                            )
                        },
                        onExerciseGuideClick = { name ->
                            activeSuggestionGuide = ExerciseGuides.findByName(name)
                        }
                    )
                }

                item(key = "light_suggestion") {
                    SuggestionCard(
                        option = uiState.recommendation.lightOption,
                        isIdeal = false,
                        isAiGenerated = uiState.recommendation.isAiGenerated,
                        onStartWorkoutClick = {
                            viewModel.startWorkoutFromSuggestion(
                                option = uiState.recommendation.lightOption,
                                isIdeal = false
                            )
                        },
                        onCompleteClick = {
                            viewModel.completeSuggestion(
                                option = uiState.recommendation.lightOption,
                                isIdeal = false
                            )
                        },
                        onExerciseGuideClick = { name ->
                            activeSuggestionGuide = ExerciseGuides.findByName(name)
                        }
                    )
                }

                // 6. Recent History (up to 5 items or warm empty state)
                item(key = "history_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = stringResource(R.string.section_recent_history),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (uiState.allSessions.isNotEmpty()) {
                            Text(
                                text = "${uiState.recentSessions.size} dari ${uiState.allSessions.size} sesi",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (uiState.recentSessions.isEmpty()) {
                    item(key = "empty_history") {
                        EmptyHistoryCard()
                    }
                } else {
                    items(
                        items = uiState.recentSessions,
                        key = { it.id }
                    ) { session ->
                        HistorySessionCard(
                            session = session,
                            onEditRequest = { editingSession = session },
                            onDeleteRequest = { pendingDeleteSession = session }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    pendingDeleteSession?.let { sessionToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSession = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = stringResource(R.string.delete_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(sessionToDelete.id)
                        pendingDeleteSession = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = extendedColors.dangerTint
                    ),
                    modifier = Modifier.testTag("confirm_delete_session_button")
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteSession = null },
                    modifier = Modifier.testTag("cancel_delete_session_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Manual Log Modal Bottom Sheet (Create or Edit WorkoutSession)
    if (showManualLogSheet || editingSession != null) {
        val targetEdit = editingSession
        LogWorkoutSheet(
            initialSession = targetEdit,
            onDismiss = {
                showManualLogSheet = false
                editingSession = null
            },
            onStartWorkout = if (targetEdit == null) {
                { sport, source, targetDuration, distance, avgHr, rpe, notes, items ->
                    showManualLogSheet = false
                    editingSession = null
                    viewModel.startWorkoutFromManual(
                        sportType = sport,
                        source = source,
                        targetDurationMin = targetDuration,
                        distanceKm = distance,
                        avgHr = avgHr,
                        rpe = rpe,
                        notes = notes,
                        dumbbellItems = items
                    )
                }
            } else {
                null
            },
            onSave = { sport, source, duration, distance, avgHr, rpe, notes, items ->
                if (targetEdit != null) {
                    viewModel.updateSession(
                        existing = targetEdit,
                        sportType = sport,
                        durationMin = duration,
                        distanceKm = distance,
                        avgHr = avgHr,
                        rpe = rpe,
                        notes = notes,
                        dumbbellItems = items
                    )
                } else {
                    viewModel.saveManualSession(
                        sportType = sport,
                        source = source,
                        durationMin = duration,
                        distanceKm = distance,
                        avgHr = avgHr,
                        rpe = rpe,
                        notes = notes,
                        dumbbellItems = items
                    )
                }
                showManualLogSheet = false
                editingSession = null
            }
        )
    }

    // Exercise Guide Dialog when tapping exercise chips on suggestion cards
    activeSuggestionGuide?.let { guide ->
        ExerciseGuideDialog(
            guide = guide,
            onDismiss = { activeSuggestionGuide = null }
        )
    }

    // Settings / Sync Modal Bottom Sheet
    if (showSettingsSheet) {
        val hcAvailability = remember(showSettingsSheet) {
            viewModel.checkHealthConnectAvailability()
        }
        SettingsSyncBottomSheet(
            totalSessionsCount = uiState.allSessions.size,
            isCloudSyncEnabled = uiState.isCloudSyncEnabled,
            userDisplayName = uiState.userDisplayName,
            userEmail = uiState.userEmail,
            cloudSyncStatus = uiState.cloudSyncStatus,
            onSyncCloudNow = { viewModel.syncCloudNow() },
            onSignOut = {
                showSettingsSheet = false
                onSignOut()
            },
            aiProvider = uiState.aiProvider,
            aiApiKey = uiState.activeAiApiKey,
            onSelectAiProvider = { provider -> viewModel.selectAiProvider(provider) },
            onSaveAiApiKey = { provider, key -> viewModel.saveAiApiKey(provider, key) },
            reminderSettings = uiState.reminderSettings,
            onToggleDailyReminder = { enabled ->
                if (!enabled) {
                    viewModel.setDailyReminderEnabled(false)
                } else if (NotificationHelper.hasNotificationPermission(context)) {
                    viewModel.setDailyReminderEnabled(true)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pendingNotificationAction = "enable_reminder"
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setDailyReminderEnabled(true)
                }
            },
            onChangeReminderTime = { hour, minute ->
                viewModel.setDailyReminderTime(hour, minute)
            },
            onTestNotification5Seconds = {
                if (NotificationHelper.hasNotificationPermission(context)) {
                    viewModel.triggerTestNotification()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pendingNotificationAction = "test_notification"
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.triggerTestNotification()
                }
            },
            healthConnectAvailability = hcAvailability,
            isSyncingHealthConnect = uiState.isSyncingHealthConnect,
            healthConnectStatusMessage = uiState.healthConnectStatusMessage,
            onSyncHealthConnect = {
                viewModel.triggerHealthConnectSync { requiredPermissions ->
                    healthConnectPermissionLauncher.launch(requiredPermissions)
                }
            },
            onInstallHealthConnect = {
                try {
                    context.startActivity(viewModel.healthConnectManager.createInstallOrUpdateIntent())
                } catch (_: ActivityNotFoundException) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            "Buka Play Store untuk menginstal Health Connect.",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            },
            onDismiss = { showSettingsSheet = false },
            onBuildBackupJson = { viewModel.buildFullBackupJson() },
            onParseBackupJson = { raw -> viewModel.parseBackupJson(raw) },
            onRestoreFromBackup = { snapshot, callback ->
                viewModel.restoreFromBackup(snapshot, callback)
            },
            onExportJson = { onReady -> viewModel.exportSessionsJson(onReady) },
            onImportJson = { raw, callback -> viewModel.importSessionsJson(raw, callback) },
            onClearAll = { viewModel.clearAllSessions() },
            onShowMessage = { msg ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                }
            }
        )
    }

    // Weekly AI Review Dialog (Gemini 7-day coaching summary)
    WeeklyAiReviewDialog(
        state = uiState.weeklyReviewState,
        onDismiss = { viewModel.dismissWeeklyAiReview() },
        onRetry = { viewModel.requestWeeklyAiReview() },
        onOpenSettings = { showSettingsSheet = true }
    )

    // Full-Screen Live Workout Timer overlay
        uiState.activeWorkout?.let { activeSession ->
            ActiveWorkoutScreen(
                state = activeSession,
                onTogglePause = { viewModel.togglePauseActiveWorkout() },
                onPauseForFinish = { viewModel.pauseActiveWorkoutForFinish() },
                onResumeIfNeeded = { viewModel.resumeActiveWorkoutIfPaused() },
                onToggleExerciseCompleted = { index -> viewModel.toggleActiveExerciseCompleted(index) },
                onCancelWorkout = { viewModel.cancelActiveWorkout() },
                onConfirmFinish = { durationMin, rpe, distanceKm, notes ->
                    viewModel.finishActiveWorkout(
                        durationMin = durationMin,
                        rpe = rpe,
                        distanceKm = distanceKm,
                        notes = notes
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CoachHeader(
    greetingPeriod: GreetingPeriod,
    onSettingsClick: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val greetingText = when (greetingPeriod) {
        GreetingPeriod.MORNING -> stringResource(R.string.greeting_morning)
        GreetingPeriod.AFTERNOON -> stringResource(R.string.greeting_afternoon)
        GreetingPeriod.EVENING -> stringResource(R.string.greeting_evening)
        GreetingPeriod.NIGHT -> stringResource(R.string.greeting_night)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = greetingText,
                style = MaterialTheme.typography.labelLarge,
                color = extendedColors.idealAccent
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Single Settings / Sync icon button in top-right corner
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, extendedColors.cardBorder)
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_sync_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.cd_settings_sync),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun TodayStatusSection(
    selectedStatus: TodayStatus,
    onSelectStatus: (TodayStatus) -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.section_today_status),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TodayStatus.entries.forEach { status ->
                val isSelected = selectedStatus == status
                val accent = if (status == TodayStatus.TIRED) {
                    extendedColors.lightAccent
                } else {
                    extendedColors.idealAccent
                }
                val bg = when {
                    isSelected -> accent
                    else -> MaterialTheme.colorScheme.surface
                }
                val contentColor = when {
                    isSelected && status == TodayStatus.TIRED -> MaterialTheme.colorScheme.onSecondary
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val borderColor = if (isSelected) accent else extendedColors.cardBorder

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectStatus(status) }
                        .testTag("status_chip_${status.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = status.toIcon(),
                            contentDescription = null,
                            tint = if (isSelected) contentColor else accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualLogEntryCard(
    onClick: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("manual_log_card_button"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.idealBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(extendedColors.idealContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddCircleOutline,
                    contentDescription = stringResource(R.string.cd_add_manual),
                    tint = extendedColors.idealAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.manual_log_button_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.manual_log_button_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = extendedColors.idealAccent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
