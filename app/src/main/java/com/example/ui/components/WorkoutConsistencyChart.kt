package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.DayRhythmIndicator
import com.example.ui.CloudSyncStatus
import com.example.ui.theme.AntiMagerTheme
import kotlin.math.ceil

enum class WorkoutChartMode {
    BAR,
    TREND
}

enum class ConsistencyWindow {
    ROLLING_7_DAYS,
    THIS_WEEK
}

/**
 * Native Jetpack Compose 7-day workout consistency chart (minutes per day)
 * backed by real-time Firestore session streams.
 * Supports stacked Bar & smooth Bezier Area/Line modes, interactive tap/drag scrub crosshair,
 * daily minute annotations, and an average reference line.
 */
@Composable
fun WorkoutConsistencyChart(
    days: List<DayRhythmIndicator>,
    consistencyPercent: Int,
    activeDaysCount: Int,
    averageActiveDayMinutes: Int,
    peakDayMinutes: Int,
    chartMode: WorkoutChartMode,
    window: ConsistencyWindow,
    selectedDateKey: String?,
    cloudSyncStatus: CloudSyncStatus = CloudSyncStatus.SYNCED,
    onModeChange: (WorkoutChartMode) -> Unit,
    onWindowChange: (ConsistencyWindow) -> Unit,
    onSelectDay: (String) -> Unit,
    onScrubDay: (String) -> Unit = onSelectDay,
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val selectedDay = remember(days, selectedDateKey) {
        days.firstOrNull { it.dateKey == selectedDateKey }
    }
    val hasAnyData = remember(days) { days.any { it.totalMinutes > 0 } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("workout_consistency_chart_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header row: Title + Consistency % badge + Chart Style Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.weekly_chart_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (cloudSyncStatus == CloudSyncStatus.SYNCED) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = "Data cloud tersinkron",
                            tint = extendedColors.idealAccent.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = if (activeDaysCount > 0) {
                        "$activeDaysCount/7 hari aktif ($consistencyPercent%) • rata-rata $averageActiveDayMinutes mnt"
                    } else {
                        "Ketuk atau geser grafik untuk melihat detail harian"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ChartModeToggle(
                selectedMode = chartMode,
                onModeSelected = onModeChange
            )
        }

        // Window switcher pill row (Rolling 7 Hari vs Sen-Min)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WindowToggleRow(
                selectedWindow = window,
                onWindowSelected = onWindowChange
            )

            if (averageActiveDayMinutes > 0) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = extendedColors.idealContainer
                ) {
                    Text(
                        text = "${stringResource(R.string.consistency_avg_label)} ${averageActiveDayMinutes}m",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = extendedColors.idealAccent,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Interactive Tooltip Banner (replaces hover tooltip in Recharts/D3)
        ChartTooltipBanner(
            selectedDay = selectedDay,
            hasAnyData = hasAnyData
        )

        // Main Interactive Plot Canvas
        ConsistencyPlotCanvas(
            days = days,
            chartMode = chartMode,
            selectedDateKey = selectedDateKey,
            averageMinutes = averageActiveDayMinutes,
            onSelectDay = onSelectDay,
            onScrubDay = onScrubDay
        )

        // Subtle Legend + Peak Minutes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartLegendDot(
                    color = extendedColors.idealAccent,
                    label = stringResource(R.string.weekly_strength_label)
                )
                ChartLegendDot(
                    color = extendedColors.lightAccent,
                    label = stringResource(R.string.weekly_cardio_label)
                )
            }
            if (peakDayMinutes > 0) {
                Text(
                    text = "Puncak: $peakDayMinutes mnt",
                    style = MaterialTheme.typography.labelSmall,
                    color = extendedColors.subtleInk
                )
            }
        }
    }
}

@Composable
private fun ConsistencyPlotCanvas(
    days: List<DayRhythmIndicator>,
    chartMode: WorkoutChartMode,
    selectedDateKey: String?,
    averageMinutes: Int,
    onSelectDay: (String) -> Unit,
    onScrubDay: (String) -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val strengthColor = extendedColors.idealAccent
    val cardioColor = extendedColors.lightAccent
    val gridColor = extendedColors.cardBorder
    val emptyBarColor = extendedColors.restCircle
    val surfaceColor = MaterialTheme.colorScheme.surface
    val highlightBgColor = extendedColors.idealContainer.copy(alpha = 0.55f)
    val mutedTextColor = extendedColors.subtleInk
    val valueLabelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    val textMeasurer = rememberTextMeasurer()

    val rawMax = remember(days, averageMinutes) {
        maxOf(days.maxOfOrNull { it.totalMinutes } ?: 0, averageMinutes)
    }
    val yAxisMax = remember(rawMax) {
        when {
            rawMax <= 20 -> 30
            else -> (ceil((rawMax + 5) / 15.0).toInt() * 15).coerceAtLeast(30)
        }
    }
    val yAxisMid = yAxisMax / 2

    val animatedFractions = days.map { day ->
        val target = (day.totalMinutes.toFloat() / yAxisMax.toFloat()).coerceIn(0f, 1f)
        val animated by animateFloatAsState(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "consistency_bar_${day.dateKey}"
        )
        animated
    }

    val chartDescription = remember(days) {
        "Grafik konsistensi latihan 7 hari dalam menit per hari: " + days.joinToString(", ") {
            "${it.dayLabel} ${it.totalMinutes} menit"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(extendedColors.warmSandSurface.copy(alpha = 0.55f))
            .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)
            .semantics { contentDescription = chartDescription }
            .testTag("weekly_duration_chart")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(162.dp)
        ) {
            // Y-axis ticks column (minutes)
            Column(
                modifier = Modifier
                    .width(34.dp)
                    .height(146.dp)
                    .padding(top = 12.dp, end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${yAxisMax}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedTextColor
                )
                Text(
                    text = "${yAxisMid}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedTextColor
                )
                Text(
                    text = "0m",
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedTextColor
                )
            }

            // Interactive Canvas plot area with tap + horizontal drag scrubbing
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(days) {
                            detectTapGestures { offset ->
                                if (days.isEmpty() || size.width <= 0) return@detectTapGestures
                                val slotWidth = size.width.toFloat() / days.size
                                val idx = (offset.x / slotWidth).toInt().coerceIn(0, days.lastIndex)
                                onSelectDay(days[idx].dateKey)
                            }
                        }
                        .pointerInput(days) {
                            detectHorizontalDragGestures { change, _ ->
                                if (days.isEmpty() || size.width <= 0) return@detectHorizontalDragGestures
                                val slotWidth = size.width.toFloat() / days.size
                                val idx = (change.position.x / slotWidth).toInt().coerceIn(0, days.lastIndex)
                                onScrubDay(days[idx].dateKey)
                            }
                        }
                ) {
                    val count = days.size
                    if (count == 0) return@Canvas

                    val topPad = 18.dp.toPx()
                    val bottomPad = 14.dp.toPx()
                    val plotHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                    val baselineY = topPad + plotHeight
                    val slotWidth = size.width / count.toFloat()

                    // 1. Cartesian horizontal reference grid lines
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    listOf(0f, 0.5f, 1f).forEach { ratio ->
                        val y = topPad + plotHeight * ratio
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = if (ratio == 1f) 1.2.dp.toPx() else 1.dp.toPx(),
                            pathEffect = if (ratio == 1f) null else dashEffect
                        )
                    }

                    // 2. Average reference line (when user has active minutes)
                    if (averageMinutes > 0) {
                        val avgRatio = (averageMinutes.toFloat() / yAxisMax.toFloat()).coerceIn(0f, 1f)
                        val avgY = baselineY - (plotHeight * avgRatio)
                        drawLine(
                            color = strengthColor.copy(alpha = 0.45f),
                            start = Offset(0f, avgY),
                            end = Offset(size.width, avgY),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    }

                    val pointOffsets = days.indices.map { i ->
                        val cx = slotWidth * i + slotWidth / 2f
                        val cy = baselineY - (plotHeight * animatedFractions[i])
                        Offset(cx, cy)
                    }

                    // 3. Highlight column & vertical crosshair for selected/scrubbed day
                    days.forEachIndexed { i, day ->
                        if (day.dateKey == selectedDateKey) {
                            val cx = pointOffsets[i].x
                            val bandWidth = (slotWidth * 0.76f).coerceAtMost(40.dp.toPx())
                            drawRoundRect(
                                color = highlightBgColor,
                                topLeft = Offset(cx - bandWidth / 2f, topPad - 6.dp.toPx()),
                                size = Size(bandWidth, plotHeight + 10.dp.toPx()),
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                            )
                            drawLine(
                                color = strengthColor.copy(alpha = 0.35f),
                                start = Offset(cx, topPad - 4.dp.toPx()),
                                end = Offset(cx, baselineY),
                                strokeWidth = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                        }
                    }

                    if (chartMode == WorkoutChartMode.BAR) {
                        val barWidth = (slotWidth * 0.46f).coerceIn(13.dp.toPx(), 26.dp.toPx())
                        val topRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())

                        days.forEachIndexed { i, day ->
                            val cx = pointOffsets[i].x
                            val left = cx - barWidth / 2f
                            val isSelected = day.dateKey == selectedDateKey
                            val totalH = plotHeight * animatedFractions[i]

                            if (day.totalMinutes <= 0 || totalH < 2.dp.toPx()) {
                                val stubHeight = 4.dp.toPx()
                                drawRoundRect(
                                    color = if (day.isToday) {
                                        strengthColor.copy(alpha = 0.38f)
                                    } else {
                                        emptyBarColor
                                    },
                                    topLeft = Offset(left, baselineY - stubHeight),
                                    size = Size(barWidth, stubHeight),
                                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                )
                            } else {
                                val alpha = if (selectedDateKey != null && !isSelected) 0.5f else 1f
                                val strengthRatio = day.strengthMinutes.toFloat() / day.totalMinutes.toFloat()
                                val strengthH = totalH * strengthRatio
                                val cardioH = totalH * (1f - strengthRatio)

                                if (cardioH > 0f && strengthH > 0f) {
                                    drawRoundRect(
                                        color = cardioColor.copy(alpha = alpha),
                                        topLeft = Offset(left, baselineY - cardioH),
                                        size = Size(barWidth, cardioH),
                                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )
                                    val gap = 1.5.dp.toPx().coerceAtMost(strengthH * 0.2f)
                                    drawRoundRect(
                                        color = strengthColor.copy(alpha = alpha),
                                        topLeft = Offset(left, baselineY - totalH),
                                        size = Size(barWidth, (strengthH - gap).coerceAtLeast(2.dp.toPx())),
                                        cornerRadius = topRadius
                                    )
                                } else {
                                    val barColor = if (day.strengthMinutes > 0) strengthColor else cardioColor
                                    drawRoundRect(
                                        color = barColor.copy(alpha = alpha),
                                        topLeft = Offset(left, baselineY - totalH),
                                        size = Size(barWidth, totalH),
                                        cornerRadius = topRadius
                                    )
                                }

                                // Crisp minute label directly above active bar
                                val measured = textMeasurer.measure(
                                    text = "${day.totalMinutes}m",
                                    style = valueLabelStyle.copy(
                                        color = if (isSelected) strengthColor else valueLabelStyle.color.copy(alpha = 0.8f)
                                    )
                                )
                                val labelX = (cx - measured.size.width / 2f)
                                    .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                                val labelY = (baselineY - totalH - measured.size.height - 3.dp.toPx())
                                    .coerceAtLeast(0f)
                                drawText(
                                    textLayoutResult = measured,
                                    topLeft = Offset(labelX, labelY)
                                )
                            }
                        }
                    } else {
                        // Smooth Cubic Bezier Area & Line Chart
                        val linePath = Path()
                        val areaPath = Path()

                        pointOffsets.forEachIndexed { i, pt ->
                            if (i == 0) {
                                linePath.moveTo(pt.x, pt.y)
                                areaPath.moveTo(pt.x, baselineY)
                                areaPath.lineTo(pt.x, pt.y)
                            } else {
                                val prev = pointOffsets[i - 1]
                                val controlX = (prev.x + pt.x) / 2f
                                linePath.cubicTo(controlX, prev.y, controlX, pt.y, pt.x, pt.y)
                                areaPath.cubicTo(controlX, prev.y, controlX, pt.y, pt.x, pt.y)
                            }
                        }
                        val lastPt = pointOffsets.last()
                        areaPath.lineTo(lastPt.x, baselineY)
                        areaPath.close()

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    strengthColor.copy(alpha = 0.30f),
                                    strengthColor.copy(alpha = 0.02f)
                                ),
                                startY = topPad,
                                endY = baselineY
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = strengthColor,
                            style = Stroke(
                                width = 2.6.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        pointOffsets.forEachIndexed { i, pt ->
                            val day = days[i]
                            val isSelected = day.dateKey == selectedDateKey
                            val nodeColor = when {
                                day.cardioMinutes > day.strengthMinutes -> cardioColor
                                day.totalMinutes > 0 -> strengthColor
                                else -> emptyBarColor
                            }
                            if (isSelected) {
                                drawCircle(
                                    color = nodeColor.copy(alpha = 0.22f),
                                    radius = 9.dp.toPx(),
                                    center = pt
                                )
                            }
                            drawCircle(
                                color = surfaceColor,
                                radius = if (isSelected) 5.5.dp.toPx() else 4.2.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = nodeColor,
                                radius = if (isSelected) 3.8.dp.toPx() else 2.8.dp.toPx(),
                                center = pt
                            )

                            if (day.totalMinutes > 0 && (isSelected || selectedDateKey == null)) {
                                val measured = textMeasurer.measure(
                                    text = "${day.totalMinutes}m",
                                    style = valueLabelStyle.copy(
                                        color = if (isSelected) strengthColor else valueLabelStyle.color.copy(alpha = 0.78f)
                                    )
                                )
                                val labelX = (pt.x - measured.size.width / 2f)
                                    .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                                val labelY = (pt.y - measured.size.height - 7.dp.toPx()).coerceAtLeast(0f)
                                drawText(
                                    textLayoutResult = measured,
                                    topLeft = Offset(labelX, labelY)
                                )
                            }
                        }
                    }
                }
            }
        }

        // X-axis day labels row aligned with the 7 slots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 34.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEach { day ->
                val isSelected = day.dateKey == selectedDateKey
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectDay(day.dateKey) }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = when {
                            isSelected -> extendedColors.idealAccent
                            day.isToday -> MaterialTheme.colorScheme.onSurface
                            else -> mutedTextColor
                        },
                        textAlign = TextAlign.Center
                    )
                    if (day.isToday) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(extendedColors.idealAccent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowToggleRow(
    selectedWindow: ConsistencyWindow,
    onWindowSelected: (ConsistencyWindow) -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            ConsistencyWindow.ROLLING_7_DAYS to stringResource(R.string.consistency_range_rolling_7d),
            ConsistencyWindow.THIS_WEEK to stringResource(R.string.consistency_range_this_week)
        ).forEach { (window, label) ->
            val selected = selectedWindow == window
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) extendedColors.idealAccent else extendedColors.warmSandSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) extendedColors.idealAccent else extendedColors.cardBorder
                ),
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onWindowSelected(window) }
                    .testTag("chart_window_${window.name.lowercase()}")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartModeToggle(
    selectedMode: WorkoutChartMode,
    onModeSelected: (WorkoutChartMode) -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = extendedColors.warmSandSurface,
        border = BorderStroke(1.dp, extendedColors.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ChartModeChip(
                selected = selectedMode == WorkoutChartMode.BAR,
                icon = Icons.Rounded.BarChart,
                label = stringResource(R.string.consistency_mode_bar),
                testTag = "chart_mode_bar",
                onClick = { onModeSelected(WorkoutChartMode.BAR) }
            )
            ChartModeChip(
                selected = selectedMode == WorkoutChartMode.TREND,
                icon = Icons.Rounded.ShowChart,
                label = stringResource(R.string.consistency_mode_trend),
                testTag = "chart_mode_trend",
                onClick = { onModeSelected(WorkoutChartMode.TREND) }
            )
        }
    }
}

@Composable
private fun ChartModeChip(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) extendedColors.idealAccent else extendedColors.subtleInk,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) MaterialTheme.colorScheme.onSurface else extendedColors.subtleInk
            )
        }
    }
}

@Composable
private fun ChartTooltipBanner(
    selectedDay: DayRhythmIndicator?,
    hasAnyData: Boolean
) {
    val extendedColors = AntiMagerTheme.extendedColors

    AnimatedContent(
        targetState = selectedDay,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "chart_tooltip_banner"
    ) { day ->
        if (day != null) {
            val datePrefix = buildString {
                append(day.dayLabel)
                if (day.shortDateLabel.isNotBlank()) {
                    append(", ")
                    append(day.shortDateLabel)
                }
                if (day.isToday) append(" (Hari ini)")
            }
            val detailText = if (day.totalMinutes > 0) {
                val parts = buildList {
                    if (day.strengthMinutes > 0) add("Beban ${day.strengthMinutes} mnt")
                    if (day.cardioMinutes > 0) add("Kardio ${day.cardioMinutes} mnt")
                }
                val breakdown = if (parts.isNotEmpty()) " • ${parts.joinToString(" + ")}" else ""
                "$datePrefix: ${day.totalMinutes} menit (${day.sessionCount} sesi)$breakdown"
            } else if (day.isFuture) {
                "$datePrefix: Belum berlangsung — bebas atur ritme nanti"
            } else {
                "$datePrefix: 0 menit — hari pemulihan tetap bagian dari progres"
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (day.totalMinutes > 0) extendedColors.idealContainer else extendedColors.warmSandSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chart_tooltip_banner")
            ) {
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day.totalMinutes > 0) extendedColors.idealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        } else if (!hasAnyData) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = extendedColors.warmSandSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.weekly_chart_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ChartLegendDot(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
