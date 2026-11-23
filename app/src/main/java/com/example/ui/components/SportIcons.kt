package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Battery3Bar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.model.SportType
import com.example.model.TodayStatus

fun SportType.toIcon(): ImageVector = when (this) {
    SportType.RUNNING -> Icons.AutoMirrored.Rounded.DirectionsRun
    SportType.BADMINTON -> Icons.Rounded.SportsTennis
    SportType.DUMBBELL -> Icons.Rounded.FitnessCenter
    SportType.WALKING -> Icons.AutoMirrored.Rounded.DirectionsWalk
    SportType.REST -> Icons.Rounded.SelfImprovement
}

fun TodayStatus.toIcon(): ImageVector = when (this) {
    TodayStatus.FRESH -> Icons.Rounded.WbSunny
    TodayStatus.TIRED -> Icons.Rounded.Battery3Bar
    TodayStatus.BUSY -> Icons.Rounded.Schedule
}
