package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ExerciseGuide
import com.example.data.ExerciseGuides
import com.example.data.WorkoutTemplate
import com.example.data.WorkoutTemplates
import com.example.model.DumbbellItem
import com.example.model.SessionSource
import com.example.model.SportType
import com.example.model.WorkoutSession
import com.example.ui.theme.AntiMagerTheme
import java.util.Locale

private val sheetSportOrder = listOf(
    SportType.DUMBBELL,
    SportType.RUNNING,
    SportType.BADMINTON,
    SportType.WALKING,
    SportType.REST
)

private fun SportType.sheetChipLabel(): String = when (this) {
    SportType.DUMBBELL -> "Beban & Bodyweight"
    SportType.RUNNING -> "Lari"
    SportType.BADMINTON -> "Badminton"
    SportType.WALKING -> "Jalan"
    SportType.REST -> "Rest"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogWorkoutSheet(
    initialSession: WorkoutSession? = null,
    onDismiss: () -> Unit,
    onStartWorkout: ((
        sportType: SportType,
        source: SessionSource,
        targetDurationMin: Int?,
        distanceKm: Double?,
        avgHr: Int?,
        rpe: Int,
        notes: String?,
        dumbbellItems: List<DumbbellItem>
    ) -> Unit)? = null,
    onSave: (
        sportType: SportType,
        source: SessionSource,
        durationMin: Int,
        distanceKm: Double?,
        avgHr: Int?,
        rpe: Int,
        notes: String?,
        dumbbellItems: List<DumbbellItem>
    ) -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditMode = initialSession != null

    var selectedSport by remember(initialSession?.id) {
        mutableStateOf(initialSession?.sportType ?: SportType.DUMBBELL)
    }
    var selectedSource by remember(initialSession?.id) {
        mutableStateOf(initialSession?.source ?: SessionSource.MANUAL)
    }
    var durationText by remember(initialSession?.id) {
        mutableStateOf((initialSession?.durationMin ?: 20).toString())
    }
    var distanceText by remember(initialSession?.id) {
        mutableStateOf(initialSession?.distanceKm?.let { formatStepperWeight(it) } ?: "")
    }
    var avgHrText by remember(initialSession?.id) {
        mutableStateOf(initialSession?.avgHr?.toString() ?: "")
    }
    var selectedRpe by remember(initialSession?.id) {
        mutableIntStateOf(initialSession?.rpe ?: 4)
    }
    var notesText by remember(initialSession?.id) {
        mutableStateOf(initialSession?.notes ?: "")
    }
    var durationError by remember(initialSession?.id) { mutableStateOf<String?>(null) }

    // Strength / Bodyweight editable list + template state
    var activeTemplateId by remember(initialSession?.id) { mutableStateOf<String?>(null) }
    val workoutItems = remember(initialSession?.id) {
        mutableStateListOf<DumbbellItem>().apply {
            initialSession?.dumbbellItems?.let { addAll(it) }
        }
    }

    // Sub-dialogs inside sheet
    var activeGuide by remember { mutableStateOf<ExerciseGuide?>(null) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("log_workout_sheet"),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEditMode) {
                            stringResource(R.string.edit_workout_title)
                        } else {
                            stringResource(R.string.manual_log_button_title)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isEditMode) {
                            "Perbarui durasi, RPE, daftar gerakan, atau catatan — langsung tersinkron ke cloud."
                        } else {
                            "Catat santai sesuai aktivitas hari ini — bebas pakai template atau atur sendiri."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close)
                    )
                }
            }

            // 1. Sport Type Selector (Beban & Bodyweight, Lari, Badminton, Jalan, Rest)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Jenis Olahraga",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sheetSportOrder.forEach { sport ->
                        val isSelected = selectedSport == sport
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSport = sport },
                            label = { Text(sport.sheetChipLabel()) },
                            leadingIcon = {
                                Icon(
                                    imageVector = sport.toIcon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = extendedColors.idealContainer,
                                selectedLabelColor = extendedColors.idealAccent,
                                selectedLeadingIconColor = extendedColors.idealAccent,
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = extendedColors.cardBorder,
                                selectedBorderColor = extendedColors.idealAccent
                            ),
                            modifier = Modifier
                                .heightIn(min = 42.dp)
                                .testTag("sport_chip_${sport.name.lowercase()}")
                        )
                    }
                }
            }

            // 2. Duration Quick Chips (15, 20, 30, 45, 60 mnt) + Custom Input
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Durasi Latihan",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 20, 30, 45, 60).forEach { preset ->
                        val selected = durationText == preset.toString()
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) extendedColors.idealAccent else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (selected) extendedColors.idealAccent else extendedColors.cardBorder
                            ),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    durationText = preset.toString()
                                    durationError = null
                                }
                                .testTag("duration_chip_$preset")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "$preset mnt",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { value ->
                        durationText = value.filter { it.isDigit() }.take(3)
                        durationError = null
                    },
                    label = { Text("Durasi manual (menit)") },
                    isError = durationError != null,
                    supportingText = durationError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_duration_input")
                )
            }

            // 3. Running / Walking Distance + Optional Avg HR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedSport == SportType.RUNNING || selectedSport == SportType.WALKING) {
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { value ->
                            distanceText = value.filter { it.isDigit() || it == '.' || it == ',' }
                                .replace(',', '.')
                                .take(6)
                        },
                        label = { Text("Jarak (km)") },
                        placeholder = { Text("Opsional") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_distance_input")
                    )
                }

                OutlinedTextField(
                    value = avgHrText,
                    onValueChange = { value ->
                        avgHrText = value.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Rata-rata HR (bpm)") },
                    placeholder = { Text("Opsional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_hr_input")
                )
            }

            // 4. Strength & Bodyweight Templates + Editable Exercise List
            if (selectedSport == SportType.DUMBBELL) {
                StrengthWorkoutSection(
                    activeTemplateId = activeTemplateId,
                    items = workoutItems,
                    onApplyTemplate = { template ->
                        activeTemplateId = template.id
                        workoutItems.clear()
                        workoutItems.addAll(template.items)
                    },
                    onUpdateItem = { index, updated ->
                        workoutItems[index] = updated
                    },
                    onRemoveItem = { index ->
                        workoutItems.removeAt(index)
                    },
                    onShowGuide = { exerciseName ->
                        activeGuide = ExerciseGuides.findByName(exerciseName)
                    },
                    onOpenAddDialog = { showAddExerciseDialog = true }
                )
            }

            // 5. RPE 1-10 Picker with short helper label
            RpeSelectorCard(
                selectedRpe = selectedRpe,
                onSelectRpe = { selectedRpe = it }
            )

            // 6. Optional Notes
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Catatan singkat (opsional)") },
                placeholder = { Text("Misal: Badan terasa lebih segar, napas aman") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_notes_input")
            )

            // 7. Source selector (Manual / Strava / Health) — immutable when editing existing session
            if (!isEditMode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sumber Catatan",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SessionSource.entries.forEach { source ->
                            val selected = selectedSource == source
                            FilterChip(
                                selected = selected,
                                onClick = { selectedSource = source },
                                label = { Text(source.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = extendedColors.idealContainer,
                                    selectedLabelColor = extendedColors.idealAccent,
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = extendedColors.cardBorder,
                                    selectedBorderColor = extendedColors.idealAccent
                                )
                            )
                        }
                    }
                }
            }

            // Bottom Action Buttons: Create mode has Primary "Mulai Latihan ▶" + Secondary "Simpan Langsung";
            // Edit mode keeps single "Simpan Perubahan" button.
            if (isEditMode || onStartWorkout == null) {
                Button(
                    onClick = {
                        val duration = durationText.toIntOrNull()
                        if (duration == null || duration <= 0) {
                            durationError = "Masukkan durasi minimal 1 menit"
                            return@Button
                        }
                        val distance = distanceText.toDoubleOrNull()?.takeIf { it > 0.0 }
                        val avgHr = avgHrText.toIntOrNull()?.takeIf { it in 35..230 }
                        onSave(
                            selectedSport,
                            selectedSource,
                            duration,
                            distance,
                            avgHr,
                            selectedRpe,
                            notesText.trim().takeIf { it.isNotEmpty() },
                            workoutItems.toList()
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
                        .testTag("save_manual_workout_button")
                ) {
                    Text(
                        text = if (isEditMode) {
                            stringResource(R.string.action_update_workout)
                        } else {
                            stringResource(R.string.action_save_workout)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val targetDuration = durationText.toIntOrNull()?.takeIf { it > 0 }
                            val distance = distanceText.toDoubleOrNull()?.takeIf { it > 0.0 }
                            val avgHr = avgHrText.toIntOrNull()?.takeIf { it in 35..230 }
                            onStartWorkout(
                                selectedSport,
                                selectedSource,
                                targetDuration,
                                distance,
                                avgHr,
                                selectedRpe,
                                notesText.trim().takeIf { it.isNotEmpty() },
                                workoutItems.toList()
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
                            .testTag("start_manual_workout_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_start_workout),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val duration = durationText.toIntOrNull()
                            if (duration == null || duration <= 0) {
                                durationError = "Masukkan durasi minimal 1 menit"
                                return@OutlinedButton
                            }
                            val distance = distanceText.toDoubleOrNull()?.takeIf { it > 0.0 }
                            val avgHr = avgHrText.toIntOrNull()?.takeIf { it in 35..230 }
                            onSave(
                                selectedSport,
                                selectedSource,
                                duration,
                                distance,
                                avgHr,
                                selectedRpe,
                                notesText.trim().takeIf { it.isNotEmpty() },
                                workoutItems.toList()
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, extendedColors.idealBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = extendedColors.idealAccent
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("save_manual_workout_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_save_direct),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

    // Exercise Guide Dialog (opened from `?` Info icon on any exercise)
    activeGuide?.let { guide ->
        ExerciseGuideDialog(
            guide = guide,
            onDismiss = { activeGuide = null }
        )
    }

    // Add Exercise Picker / Custom Dialog
    if (showAddExerciseDialog) {
        AddExerciseDialog(
            onDismiss = { showAddExerciseDialog = false },
            onAddExercise = { item ->
                workoutItems.add(item)
                showAddExerciseDialog = false
            },
            onPreviewGuide = { guide ->
                activeGuide = guide
            }
        )
    }
}

@Composable
private fun StrengthWorkoutSection(
    activeTemplateId: String?,
    items: List<DumbbellItem>,
    onApplyTemplate: (WorkoutTemplate) -> Unit,
    onUpdateItem: (Int, DumbbellItem) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onShowGuide: (String) -> Unit,
    onOpenAddDialog: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 4 Preset Templates
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = extendedColors.idealAccent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Template Siap Pakai",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutTemplates.allTemplates.chunked(2).forEach { rowTemplates ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTemplates.forEach { template ->
                        val isSelected = activeTemplateId == template.id
                        val isBodyweight = template.id == "bodyweight_basic"
                        val accent = if (isBodyweight) extendedColors.lightAccent else extendedColors.idealAccent
                        val selectedBg = if (isBodyweight) extendedColors.lightContainer else extendedColors.idealContainer

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) selectedBg else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) accent else extendedColors.cardBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onApplyTemplate(template) }
                                .testTag("template_button_${template.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else selectedBg
                                ) {
                                    Text(
                                        text = template.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = template.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${template.items.size} gerakan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Editable Exercise List
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, extendedColors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Gerakan (${items.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap ? untuk panduan",
                        style = MaterialTheme.typography.labelSmall,
                        color = extendedColors.subtleInk
                    )
                }

                if (items.isEmpty()) {
                    Text(
                        text = "Pilih salah satu template di atas atau tambahkan gerakan manual di bawah.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    items.forEachIndexed { index, item ->
                        EditableExerciseRow(
                            item = item,
                            index = index,
                            onChange = { updated -> onUpdateItem(index, updated) },
                            onRemove = { onRemoveItem(index) },
                            onInfoClick = { onShowGuide(item.exerciseName) }
                        )
                        if (index < items.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = extendedColors.cardBorder
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenAddDialog,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, extendedColors.idealAccent),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = extendedColors.idealAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("add_exercise_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_add_exercise),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+ Tambah Gerakan",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun EditableExerciseRow(
    item: DumbbellItem,
    index: Int,
    onChange: (DumbbellItem) -> Unit,
    onRemove: () -> Unit,
    onInfoClick: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val isTimed = ExerciseGuides.isTimedExercise(item.exerciseName)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exercise_row_$index"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title row: Exercise name + `?` Info button + Trash delete button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.exerciseName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("exercise_info_button_$index")
            ) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = "Panduan ${item.exerciseName}",
                    tint = extendedColors.idealAccent,
                    modifier = Modifier.size(21.dp)
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("remove_exercise_button_$index")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_remove_exercise),
                    tint = extendedColors.subtleInk,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Steppers row: Beban (kg / Tanpa Beban), Sets, Reps / dtk
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Weight stepper
            StepperControlRow(
                label = "Beban",
                displayValue = if (item.weightKg <= 0.0) {
                    "Tanpa Beban"
                } else {
                    "${formatStepperWeight(item.weightKg)} kg"
                },
                isSoftTag = item.weightKg <= 0.0,
                onDecrement = {
                    val nextWeight = (item.weightKg - 1.0).coerceAtLeast(0.0)
                    onChange(item.copy(weightKg = nextWeight))
                },
                onIncrement = {
                    val nextWeight = (item.weightKg + 1.0).coerceAtMost(100.0)
                    onChange(item.copy(weightKg = nextWeight))
                },
                decrementEnabled = item.weightKg > 0.0
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sets stepper
                CompactStepperBox(
                    label = "Sets",
                    valueText = "${item.sets} set",
                    onDecrement = {
                        onChange(item.copy(sets = (item.sets - 1).coerceAtLeast(1)))
                    },
                    onIncrement = {
                        onChange(item.copy(sets = (item.sets + 1).coerceAtMost(12)))
                    },
                    decrementEnabled = item.sets > 1,
                    modifier = Modifier.weight(1f)
                )

                // Reps or Detik (dtk) stepper
                CompactStepperBox(
                    label = if (isTimed) "Durasi" else "Reps",
                    valueText = if (isTimed) "${item.reps} dtk" else "${item.reps} reps",
                    onDecrement = {
                        val step = if (isTimed) 5 else 1
                        val minVal = if (isTimed) 5 else 1
                        onChange(item.copy(reps = (item.reps - step).coerceAtLeast(minVal)))
                    },
                    onIncrement = {
                        val step = if (isTimed) 5 else 1
                        val maxVal = if (isTimed) 300 else 100
                        onChange(item.copy(reps = (item.reps + step).coerceAtMost(maxVal)))
                    },
                    decrementEnabled = item.reps > (if (isTimed) 5 else 1),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StepperControlRow(
    label: String,
    displayValue: String,
    isSoftTag: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean
) {
    val extendedColors = AntiMagerTheme.extendedColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extendedColors.warmSandSurface)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StepperIconButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Kurangi $label",
                enabled = decrementEnabled,
                onClick = onDecrement
            )

            if (isSoftTag) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = extendedColors.idealContainer
                ) {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.labelMedium,
                        color = extendedColors.idealAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            } else {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            StepperIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Tambah $label",
                enabled = true,
                onClick = onIncrement
            )
        }
    }
}

@Composable
private fun CompactStepperBox(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(extendedColors.warmSandSurface)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            StepperIconButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Kurangi $label",
                enabled = decrementEnabled,
                onClick = onDecrement
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            StepperIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Tambah $label",
                enabled = true,
                onClick = onIncrement
            )
        }
    }
}

@Composable
private fun StepperIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) MaterialTheme.colorScheme.surface else extendedColors.restCircle.copy(alpha = 0.6f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) extendedColors.idealAccent else extendedColors.subtleInk.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun RpeSelectorCard(
    selectedRpe: Int,
    onSelectRpe: (Int) -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val accent = if (selectedRpe >= 7) extendedColors.lightAccent else extendedColors.idealAccent
    val badgeBg = if (selectedRpe >= 7) extendedColors.lightContainer else extendedColors.idealContainer

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tingkat Usaha (RPE 1–10)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = "RPE $selectedRpe • ${rpeShortLabel(selectedRpe)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Two tidy 5-button rows (1..5 and 6..10) with comfortable 44dp touch targets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1..5, 6..10).forEach { range ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        range.forEach { value ->
                            val isSelected = selectedRpe == value
                            val chipAccent = if (value >= 7) extendedColors.lightAccent else extendedColors.idealAccent
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) chipAccent else extendedColors.warmSandSurface,
                                border = if (isSelected) null else BorderStroke(1.dp, extendedColors.cardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectRpe(value) }
                                    .testTag("rpe_button_$value")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$value",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = when {
                                            isSelected && value >= 7 -> MaterialTheme.colorScheme.onSecondary
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = rpeHelperText(selectedRpe),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddExerciseDialog(
    onDismiss: () -> Unit,
    onAddExercise: (DumbbellItem) -> Unit,
    onPreviewGuide: (ExerciseGuide) -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    var customName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = "Tambah Gerakan",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Custom exercise input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Gerakan custom") },
                        placeholder = { Text("Ketik nama gerakan...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val trimmed = customName.trim()
                            if (trimmed.isNotEmpty()) {
                                val isTimed = ExerciseGuides.isTimedExercise(trimmed)
                                onAddExercise(
                                    DumbbellItem(
                                        exerciseName = trimmed,
                                        weightKg = 0.0,
                                        sets = 3,
                                        reps = if (isTimed) 30 else 10
                                    )
                                )
                            }
                        },
                        enabled = customName.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extendedColors.idealAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Tambah")
                    }
                }

                HorizontalDivider(color = extendedColors.cardBorder)

                Text(
                    text = "Bodyweight Dasar (Tanpa Alat)",
                    style = MaterialTheme.typography.labelLarge,
                    color = extendedColors.lightAccent
                )
                ExerciseGuides.bodyweightBasic.forEach { guide ->
                    ExercisePickRow(
                        guide = guide,
                        onSelect = {
                            val isTimed = ExerciseGuides.isTimedExercise(guide.name)
                            onAddExercise(
                                DumbbellItem(
                                    exerciseName = guide.name,
                                    weightKg = 0.0,
                                    sets = 3,
                                    reps = if (isTimed) 30 else 10
                                )
                            )
                        },
                        onInfoClick = { onPreviewGuide(guide) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Gerakan Dumbbell",
                    style = MaterialTheme.typography.labelLarge,
                    color = extendedColors.idealAccent
                )
                ExerciseGuides.dumbbellGuides.forEach { guide ->
                    ExercisePickRow(
                        guide = guide,
                        onSelect = {
                            onAddExercise(
                                DumbbellItem(
                                    exerciseName = guide.name,
                                    weightKg = 5.0,
                                    sets = 3,
                                    reps = 10
                                )
                            )
                        },
                        onInfoClick = { onPreviewGuide(guide) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ExercisePickRow(
    guide: ExerciseGuide,
    onSelect: () -> Unit,
    onInfoClick: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = extendedColors.warmSandSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = guide.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = guide.targetMuscle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = "Panduan ${guide.name}",
                    tint = extendedColors.idealAccent,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

internal fun formatStepperWeight(kg: Double): String {
    return if (kg % 1.0 == 0.0) {
        kg.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", kg)
    }
}

private fun rpeShortLabel(rpe: Int): String = when (rpe) {
    1, 2 -> "Sangat Ringan"
    3, 4 -> "Santai"
    5, 6 -> "Sedang"
    7, 8 -> "Cukup Berat"
    9 -> "Sangat Berat"
    else -> "Maksimal"
}

private fun rpeHelperText(rpe: Int): String = when (rpe) {
    1, 2 -> "Napas sangat tenang, terasa seperti peregangan atau jalan santai."
    3, 4 -> "Ritme nyaman — masih bisa mengobrol penuh tanpa terengah (talk test)."
    5, 6 -> "Usaha mantap dan hangat di otot, masih terkendali untuk bicara singkat."
    7, 8 -> "Cukup menantang — besok disarankan pemulihan aktif atau upper-core ringan."
    else -> "Intensitas puncak — pastikan tidur, hidrasi, dan istirahat cukup setelah ini."
}
