package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.DateTimeHelper
import com.example.model.SessionSource
import com.example.model.SportType
import com.example.model.WorkoutSession
import com.example.ui.theme.AntiMagerTheme
import java.util.Locale

@Composable
fun EmptyHistoryCard(modifier: Modifier = Modifier) {
    val extendedColors = AntiMagerTheme.extendedColors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_history_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.cardBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_calm),
                contentDescription = stringResource(R.string.cd_empty_illustration),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.empty_history_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistorySessionCard(
    session: WorkoutSession,
    onEditRequest: () -> Unit = {},
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val isCalmLight = session.sportType == SportType.WALKING || session.sportType == SportType.REST
    val iconTint = if (isCalmLight) extendedColors.lightAccent else extendedColors.idealAccent
    val iconBg = if (isCalmLight) extendedColors.lightContainer else extendedColors.idealContainer

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_session_card_${session.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = session.sportType.toIcon(),
                        contentDescription = session.sportType.displayName,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = session.sportType.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (session.source == SessionSource.STRAVA || session.source == SessionSource.HEALTH) {
                            SourceBadge(source = session.source)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = DateTimeHelper.formatSessionDisplayDate(session.startedAtIso),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onEditRequest,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("edit_session_button_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.cd_edit_session),
                        tint = extendedColors.subtleInk,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteRequest,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_session_button_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_session),
                        tint = extendedColors.subtleInk,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics chips row (duration, distance, avgHr, RPE, dumbbell exercise count)
            FlowRow(
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricPill(
                    icon = Icons.Rounded.Schedule,
                    text = "${session.durationMin} mnt",
                    containerColor = extendedColors.warmSandSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )

                session.distanceKm?.let { km ->
                    MetricPill(
                        icon = Icons.Rounded.Straighten,
                        text = String.format(Locale.US, "%.1f km", km),
                        containerColor = extendedColors.warmSandSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }

                session.avgHr?.let { hr ->
                    MetricPill(
                        icon = Icons.Rounded.Favorite,
                        text = "$hr bpm",
                        containerColor = extendedColors.heartRateContainer,
                        contentColor = extendedColors.heartRateTint
                    )
                }

                MetricPill(
                    icon = null,
                    text = "RPE ${session.rpe}/10",
                    containerColor = if (session.rpe >= 7) {
                        extendedColors.lightContainer
                    } else {
                        extendedColors.idealContainer
                    },
                    contentColor = if (session.rpe >= 7) {
                        extendedColors.lightAccent
                    } else {
                        extendedColors.idealAccent
                    }
                )

                if (session.dumbbellItems.isNotEmpty()) {
                    val totalSets = session.dumbbellItems.sumOf { it.sets }
                    MetricPill(
                        icon = Icons.Rounded.FitnessCenter,
                        text = "${session.dumbbellItems.size} gerakan • $totalSets set",
                        containerColor = extendedColors.idealContainer,
                        contentColor = extendedColors.idealAccent
                    )
                }
            }

            if (session.dumbbellItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val summaryNames = session.dumbbellItems.joinToString(" • ") { item ->
                    val isTimed = com.example.data.ExerciseGuides.isTimedExercise(item.exerciseName)
                    val repSuffix = if (isTimed) "${item.sets}x${item.reps} dtk" else "${item.sets}x${item.reps}"
                    if (item.weightKg > 0.0) {
                        "${item.exerciseName} (${formatWeight(item.weightKg)} kg, $repSuffix)"
                    } else {
                        "${item.exerciseName} (Tanpa Beban, $repSuffix)"
                    }
                }
                Text(
                    text = summaryNames,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            session.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = extendedColors.subtleInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(source: SessionSource) {
    val extendedColors = AntiMagerTheme.extendedColors
    val (bg, fg) = when (source) {
        SessionSource.STRAVA -> extendedColors.stravaContainer to extendedColors.stravaTint
        SessionSource.HEALTH -> extendedColors.healthContainer to extendedColors.healthTint
        SessionSource.MANUAL -> extendedColors.warmSandSurface to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Text(
            text = source.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetricPill(
    icon: ImageVector?,
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}

private fun formatWeight(kg: Double): String {
    return if (kg % 1.0 == 0.0) {
        kg.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", kg)
    }
}
