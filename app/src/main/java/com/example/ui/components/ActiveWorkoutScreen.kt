package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.ExerciseGuide
import com.example.data.ExerciseGuides
import com.example.model.ActiveWorkoutState
import com.example.model.ActiveWorkoutTimerMath
import com.example.model.DumbbellItem
import com.example.model.SportType
import com.example.ui.theme.AntiMagerTheme
import kotlinx.coroutines.delay

@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutState,
    onTogglePause: () -> Unit,
    onPauseForFinish: () -> Unit,
    onResumeIfNeeded: () -> Unit,
    onToggleExerciseCompleted: (Int) -> Unit,
    onCancelWorkout: () -> Unit,
    onConfirmFinish: (durationMin: Int, rpe: Int, distanceKm: Double?, notes: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors
    var nowEpochMs by remember(state.startedAtEpochMs, state.pausedAtEpochMs) {
        mutableLongStateOf(System.currentTimeMillis())
    }

    // Wall-clock refresh loop: only triggers recomposition while running; elapsed time is always
    // computed from System.currentTimeMillis() - startedAtEpochMs - accumulatedPausedMs.
    LaunchedEffect(state.startedAtEpochMs, state.pausedAtEpochMs, state.accumulatedPausedMs) {
        if (!state.isPaused) {
            while (true) {
                nowEpochMs = System.currentTimeMillis()
                delay(250L)
            }
        }
    }

    val elapsedMillis = state.elapsedMillis(nowEpochMs)
    val formattedStopwatch = ActiveWorkoutTimerMath.formatStopwatchTime(elapsedMillis)

    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var activeGuideDialog by remember { mutableStateOf<ExerciseGuide?>(null) }
    var finishDialogElapsedMs by remember { mutableStateOf<Long?>(null) }
    var wasRunningBeforeFinish by remember { mutableStateOf(false) }

    // Intercept system back to prevent accidentally losing an active workout
    BackHandler {
        if (activeGuideDialog != null) {
            activeGuideDialog = null
        } else if (finishDialogElapsedMs != null) {
            finishDialogElapsedMs = null
            if (wasRunningBeforeFinish) onResumeIfNeeded()
        } else {
            showCancelConfirmDialog = true
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("active_workout_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            ActiveWorkoutBottomControls(
                isPaused = state.isPaused,
                onTogglePause = onTogglePause,
                onFinishClick = {
                    val snapshotNow = System.currentTimeMillis()
                    wasRunningBeforeFinish = !state.isPaused
                    val snapshotElapsed = state.elapsedMillis(snapshotNow)
                    onPauseForFinish()
                    finishDialogElapsedMs = snapshotElapsed
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Top bar: Sport Badge + Target Duration + small Batal button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = extendedColors.idealContainer,
                            border = BorderStroke(1.dp, extendedColors.idealBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = state.sportType.toIcon(),
                                    contentDescription = state.sportType.displayName,
                                    tint = extendedColors.idealAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = state.sportType.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extendedColors.idealAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (!state.targetDurationLabel.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, extendedColors.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${stringResource(R.string.active_workout_target_prefix)} ${state.targetDurationLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { showCancelConfirmDialog = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = extendedColors.subtleInk
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("active_workout_cancel_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Header Title & Kicker
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.active_workout_kicker),
                        style = MaterialTheme.typography.labelMedium,
                        color = extendedColors.idealAccent
                    )
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Main Hero Stopwatch Card
                StopwatchHeroCard(
                    formattedTime = formattedStopwatch,
                    elapsedMillis = elapsedMillis,
                    targetDurationMin = state.targetDurationMin,
                    isPaused = state.isPaused
                )

                // Interactive Exercise Checklist (when Dumbbell/Bodyweight or has exercises)
                if (state.sportType == SportType.DUMBBELL || state.dumbbellItems.isNotEmpty()) {
                    ActiveExerciseChecklistCard(
                        items = state.dumbbellItems,
                        completedIndices = state.completedExerciseIndices,
                        onToggleExercise = onToggleExerciseCompleted,
                        onOpenGuide = { exerciseName ->
                            activeGuideDialog = ExerciseGuides.findByName(exerciseName)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Cancel Confirmation Dialog
    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = stringResource(R.string.active_workout_cancel_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.active_workout_cancel_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmDialog = false
                        onCancelWorkout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = extendedColors.dangerTint
                    ),
                    modifier = Modifier.testTag("confirm_cancel_workout_button")
                ) {
                    Text(stringResource(R.string.active_workout_cancel_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelConfirmDialog = false },
                    modifier = Modifier.testTag("keep_active_workout_button")
                ) {
                    Text(stringResource(R.string.active_workout_cancel_keep))
                }
            }
        )
    }

    // Exercise Guide Dialog (does NOT stop or reset the timer)
    activeGuideDialog?.let { guide ->
        ExerciseGuideDialog(
            guide = guide,
            onDismiss = { activeGuideDialog = null }
        )
    }

    // Finish Workout Confirmation Dialog
    finishDialogElapsedMs?.let { finalElapsedMs ->
        FinishWorkoutDialog(
            state = state,
            elapsedMillis = finalElapsedMs,
            onDismiss = {
                finishDialogElapsedMs = null
                if (wasRunningBeforeFinish) {
                    onResumeIfNeeded()
                }
            },
            onConfirmSave = { durationMin, rpe, distanceKm, notes ->
                finishDialogElapsedMs = null
                onConfirmFinish(durationMin, rpe, distanceKm, notes)
            }
        )
    }
}

@Composable
private fun StopwatchHeroCard(
    formattedTime: String,
    elapsedMillis: Long,
    targetDurationMin: Int?,
    isPaused: Boolean
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val statusAccent = if (isPaused) extendedColors.lightAccent else extendedColors.idealAccent
    val statusBg = if (isPaused) extendedColors.lightContainer else extendedColors.idealContainer
    val ringTrackColor = extendedColors.restCircle

    val infiniteTransition = rememberInfiniteTransition(label = "stopwatch_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val sweepProgress = remember(elapsedMillis, targetDurationMin) {
        if (targetDurationMin != null && targetDurationMin > 0) {
            (elapsedMillis.toFloat() / (targetDurationMin * 60_000f)).coerceIn(0f, 1f)
        } else {
            ((elapsedMillis % 60_000L).toFloat() / 60_000f).coerceIn(0f, 1f)
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isPaused) extendedColors.lightBorder else extendedColors.idealBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_workout_stopwatch_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live / Paused Status Pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = statusBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                statusAccent.copy(alpha = if (isPaused) 1f else pulseAlpha)
                            )
                    )
                    Text(
                        text = if (isPaused) {
                            stringResource(R.string.active_workout_status_paused)
                        } else {
                            stringResource(R.string.active_workout_status_running)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusAccent
                    )
                }
            }

            // Circular progress halo + large crisp tabular stopwatch readout
            Box(
                modifier = Modifier.size(224.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 10.dp.toPx()
                    val inset = strokeWidth / 2f + 4.dp.toPx()
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val topLeft = Offset(inset, inset)

                    drawArc(
                        color = ringTrackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    val sweep = (sweepProgress * 360f).coerceAtLeast(if (elapsedMillis > 0L) 6f else 0f)
                    if (sweep > 0f) {
                        drawArc(
                            color = statusAccent,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = if (formattedTime.length > 5) 38.sp else 50.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.testTag("active_workout_timer_text")
                    )
                    if (targetDurationMin != null && targetDurationMin > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        val minutesDone = (elapsedMillis / 60_000L).toInt()
                        Text(
                            text = if (minutesDone >= targetDurationMin) {
                                "Target tercapai — bebas lanjut santai"
                            } else {
                                "$minutesDone / $targetDurationMin mnt"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (minutesDone >= targetDurationMin) {
                                extendedColors.idealAccent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.active_workout_calm_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActiveExerciseChecklistCard(
    items: List<DumbbellItem>,
    completedIndices: Set<Int>,
    onToggleExercise: (Int) -> Unit,
    onOpenGuide: (String) -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val doneCount = completedIndices.count { it in items.indices }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_workout_checklist_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.active_workout_checklist_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.active_workout_checklist_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (items.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = extendedColors.idealContainer
                    ) {
                        Text(
                            text = "$doneCount/${items.size} selesai",
                            style = MaterialTheme.typography.labelMedium,
                            color = extendedColors.idealAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = "Belum ada daftar gerakan khusus — fokus gerak santai sesuai ritme tubuhmu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEachIndexed { index, item ->
                    val isChecked = index in completedIndices
                    val isTimed = ExerciseGuides.isTimedExercise(item.exerciseName)
                    val weightText = if (item.weightKg <= 0.0) {
                        "Tanpa Beban"
                    } else {
                        "${formatStepperWeight(item.weightKg)} kg"
                    }
                    val repText = if (isTimed) "${item.sets}x${item.reps} dtk" else "${item.sets}x${item.reps} reps"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isChecked) extendedColors.idealContainer.copy(alpha = 0.55f)
                                else extendedColors.warmSandSurface.copy(alpha = 0.65f)
                            )
                            .clickable { onToggleExercise(index) }
                            .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                            .testTag("active_exercise_row_$index"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleExercise(index) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = extendedColors.idealAccent,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedColor = extendedColors.subtleInk
                            ),
                            modifier = Modifier.testTag("active_exercise_checkbox_$index")
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = item.exerciseName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (isChecked) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$weightText • $repText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { onOpenGuide(item.exerciseName) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("active_exercise_guide_button_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = "Panduan ${item.exerciseName}",
                                tint = extendedColors.idealAccent,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkoutBottomControls(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onFinishClick: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = extendedColors.cardBorder)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onTogglePause,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.2.dp,
                            color = if (isPaused) extendedColors.idealAccent else extendedColors.cardBorder
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isPaused) extendedColors.idealContainer else MaterialTheme.colorScheme.surface,
                            contentColor = if (isPaused) extendedColors.idealAccent else MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        modifier = Modifier
                            .weight(0.42f)
                            .heightIn(min = 54.dp)
                            .testTag("active_workout_pause_resume_button")
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPaused) {
                                stringResource(R.string.active_workout_resume)
                            } else {
                                stringResource(R.string.active_workout_pause)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = onFinishClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extendedColors.idealAccent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        modifier = Modifier
                            .weight(0.58f)
                            .heightIn(min = 54.dp)
                            .testTag("active_workout_finish_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.active_workout_finish),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FinishWorkoutDialog(
    state: ActiveWorkoutState,
    elapsedMillis: Long,
    onDismiss: () -> Unit,
    onConfirmSave: (durationMin: Int, rpe: Int, distanceKm: Double?, notes: String?) -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val formattedRealTime = remember(elapsedMillis) {
        ActiveWorkoutTimerMath.formatStopwatchTime(elapsedMillis)
    }
    val recordedMinutes = remember(elapsedMillis) {
        ActiveWorkoutTimerMath.calculateDurationMinutes(elapsedMillis)
    }
    val needsDistance = state.sportType == SportType.RUNNING || state.sportType == SportType.WALKING

    var selectedRpe by remember(state.startedAtEpochMs) {
        mutableIntStateOf(state.defaultRpe.coerceIn(1, 10))
    }
    var distanceText by remember(state.startedAtEpochMs) {
        mutableStateOf(state.initialDistanceKm?.let { formatStepperWeight(it) } ?: "")
    }
    var notesText by remember(state.startedAtEpochMs) {
        mutableStateOf(state.initialNotes ?: "")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .imePadding()
                .testTag("finish_workout_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, extendedColors.cardBorder),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(extendedColors.idealContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = extendedColors.idealAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.finish_workout_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.finish_workout_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Real-time Duration Summary Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = extendedColors.idealContainer,
                    border = BorderStroke(1.dp, extendedColors.idealBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.finish_workout_realtime_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = extendedColors.idealAccent
                            )
                            Text(
                                text = formattedRealTime,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("finish_workout_realtime_text")
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = stringResource(R.string.finish_workout_recorded_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$recordedMinutes menit",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = extendedColors.idealAccent,
                                    modifier = Modifier.testTag("finish_workout_minutes_text")
                                )
                            }
                        }
                    }
                }

                // RPE 1-10 Selector
                RpeSelectorCard(
                    selectedRpe = selectedRpe,
                    onSelectRpe = { selectedRpe = it }
                )

                // Optional Distance (km) specifically for RUNNING / WALKING
                if (needsDistance) {
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { value ->
                            distanceText = value.filter { it.isDigit() || it == '.' || it == ',' }
                                .replace(',', '.')
                                .take(6)
                        },
                        label = { Text("Jarak tempuh (km, opsional)") },
                        placeholder = { Text("Misal: 3.5") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("finish_workout_distance_input")
                    )
                }

                // Optional Notes
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Catatan singkat (opsional)") },
                    placeholder = { Text("Misal: Badan terasa lebih segar dan enteng") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("finish_workout_notes_input")
                )

                // Actions: Primary Save + Secondary Back to Timer
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            val parsedDistance = if (needsDistance) {
                                distanceText.toDoubleOrNull()?.takeIf { it > 0.0 }
                            } else {
                                null
                            }
                            onConfirmSave(
                                recordedMinutes,
                                selectedRpe,
                                parsedDistance,
                                notesText.trim().takeIf { it.isNotEmpty() }
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extendedColors.idealAccent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("finish_workout_save_button")
                    ) {
                        Text(
                            text = stringResource(R.string.finish_workout_save_session),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("finish_workout_back_button")
                        ) {
                            Text(
                                text = stringResource(R.string.finish_workout_back_to_timer),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
