package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DayRhythmIndicator
import com.example.model.WeeklyRhythmSummary
import com.example.ui.CloudSyncStatus
import com.example.ui.theme.AntiMagerTheme

@Composable
fun WeeklyRhythmCard(
    summary: WeeklyRhythmSummary,
    cloudSyncStatus: CloudSyncStatus = CloudSyncStatus.SYNCED,
    onWeeklyAiReviewClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors
    var chartMode by remember { mutableStateOf(WorkoutChartMode.BAR) }
    var window by remember { mutableStateOf(ConsistencyWindow.ROLLING_7_DAYS) }
    var selectedDateKey by remember { mutableStateOf<String?>(null) }

    val displayedDays = remember(summary, window) {
        when (window) {
            ConsistencyWindow.ROLLING_7_DAYS -> summary.last7Days
            ConsistencyWindow.THIS_WEEK -> summary.weekDays
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_rhythm_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Card Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(extendedColors.idealContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DateRange,
                        contentDescription = null,
                        tint = extendedColors.idealAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.weekly_rhythm_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics row: Active days, Total minutes, Strength vs Cardio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RhythmStatBox(
                    value = "${summary.activeDaysCount}",
                    unit = "hari",
                    label = stringResource(R.string.weekly_active_days_label),
                    modifier = Modifier.weight(1f)
                )
                RhythmStatBox(
                    value = "${summary.totalActiveMinutes}",
                    unit = "mnt",
                    label = stringResource(R.string.weekly_total_minutes_label),
                    modifier = Modifier.weight(1f)
                )
                RhythmSplitBox(
                    strengthCount = summary.strengthSessionCount,
                    cardioCount = summary.cardioSessionCount,
                    modifier = Modifier.weight(1.25f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            HorizontalDivider(
                thickness = 1.dp,
                color = extendedColors.cardBorder
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dedicated 7-Day Workout Consistency Chart (minutes/day from Firestore)
            WorkoutConsistencyChart(
                days = displayedDays,
                consistencyPercent = summary.consistencyPercent,
                activeDaysCount = summary.activeDaysCount,
                averageActiveDayMinutes = summary.averageActiveDayMinutes,
                peakDayMinutes = summary.peakDayMinutes,
                chartMode = chartMode,
                window = window,
                selectedDateKey = selectedDateKey,
                cloudSyncStatus = cloudSyncStatus,
                onModeChange = { chartMode = it },
                onWindowChange = { nextWindow ->
                    window = nextWindow
                    selectedDateKey = null
                },
                onSelectDay = { key ->
                    selectedDateKey = if (selectedDateKey == key) null else key
                },
                onScrubDay = { key ->
                    selectedDateKey = key
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                thickness = 1.dp,
                color = extendedColors.cardBorder
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 7 Day consistency indicator dots synced with the active chart window
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                displayedDays.forEach { day ->
                    DayDotItem(
                        day = day,
                        isSelected = day.dateKey == selectedDateKey,
                        onClick = {
                            selectedDateKey = if (selectedDateKey == day.dateKey) null else day.dateKey
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Compact Weekly AI Review button powered by Gemini AI
            OutlinedButton(
                onClick = onWeeklyAiReviewClick,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, extendedColors.idealBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = extendedColors.idealContainer.copy(alpha = 0.55f),
                    contentColor = extendedColors.idealAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("weekly_ai_review_button")
            ) {
                Text(
                    text = stringResource(R.string.action_weekly_ai_review),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun RhythmStatBox(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(extendedColors.warmSandSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun RhythmSplitBox(
    strengthCount: Int,
    cardioCount: Int,
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(extendedColors.warmSandSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FitnessCenter,
                contentDescription = null,
                tint = extendedColors.idealAccent,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "${stringResource(R.string.weekly_strength_label)}: $strengthCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = extendedColors.lightAccent,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "${stringResource(R.string.weekly_cardio_label)}: $cardioCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DayDotItem(
    day: DayRhythmIndicator,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        val circleBg = when {
            day.isActive -> extendedColors.idealAccent
            day.isFuture -> extendedColors.restCircle.copy(alpha = 0.55f)
            else -> extendedColors.restCircle
        }
        val circleModifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(circleBg)
            .then(
                when {
                    isSelected -> Modifier.border(
                        width = 2.dp,
                        color = extendedColors.lightAccent,
                        shape = CircleShape
                    )
                    day.isToday && !day.isActive -> Modifier.border(
                        width = 1.5.dp,
                        color = extendedColors.idealAccent,
                        shape = CircleShape
                    )
                    day.isToday && day.isActive -> Modifier.border(
                        width = 2.dp,
                        color = extendedColors.idealBorder,
                        shape = CircleShape
                    )
                    else -> Modifier
                }
            )

        Box(
            modifier = circleModifier,
            contentAlignment = Alignment.Center
        ) {
            if (day.isActive) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "${day.dayLabel} aktif ${day.totalMinutes} menit",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            } else if (day.isToday) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(extendedColors.idealAccent.copy(alpha = 0.65f))
                )
            }
        }

        Text(
            text = day.dayLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = when {
                isSelected -> extendedColors.lightAccent
                day.isToday -> extendedColors.idealAccent
                day.isActive -> MaterialTheme.colorScheme.onSurface
                else -> extendedColors.subtleInk
            }
        )
    }
}
