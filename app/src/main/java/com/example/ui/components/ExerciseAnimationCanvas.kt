package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AntiMagerTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class ExerciseMotionType {
    PUSH_UP,
    PLANK,
    SQUAT,
    GOBLET_SQUAT,
    GLUTE_BRIDGE,
    BIRD_DOG,
    OVERHEAD_PRESS,
    BENT_OVER_ROW,
    BICEP_CURL,
    LUNGE,
    FLOOR_PRESS,
    ROMANIAN_DEADLIFT,
    RUSSIAN_TWIST,
    CALF_RAISE
}

private fun resolveMotionType(exerciseName: String): ExerciseMotionType {
    val n = exerciseName.trim().lowercase()
    return when {
        n.contains("push-up") || n.contains("push up") -> ExerciseMotionType.PUSH_UP
        n.contains("plank") -> ExerciseMotionType.PLANK
        n.contains("goblet") -> ExerciseMotionType.GOBLET_SQUAT
        n.contains("squat") -> ExerciseMotionType.SQUAT
        n.contains("glute") || n.contains("bridge") -> ExerciseMotionType.GLUTE_BRIDGE
        n.contains("bird") || n.contains("dead bug") -> ExerciseMotionType.BIRD_DOG
        n.contains("overhead") || n.contains("shoulder press") || n.contains("dumbbell press") -> ExerciseMotionType.OVERHEAD_PRESS
        n.contains("floor press") -> ExerciseMotionType.FLOOR_PRESS
        n.contains("row") -> ExerciseMotionType.BENT_OVER_ROW
        n.contains("curl") -> ExerciseMotionType.BICEP_CURL
        n.contains("lunge") -> ExerciseMotionType.LUNGE
        n.contains("deadlift") || n.contains("romanian") -> ExerciseMotionType.ROMANIAN_DEADLIFT
        n.contains("twist") -> ExerciseMotionType.RUSSIAN_TWIST
        n.contains("calf") -> ExerciseMotionType.CALF_RAISE
        else -> ExerciseMotionType.SQUAT
    }
}

private fun phaseLabelFor(motionType: ExerciseMotionType, t: Float, cycle: Float): String {
    return when (motionType) {
        ExerciseMotionType.PUSH_UP -> when {
            t < 0.25f -> "Posisi Awal"
            t < 0.72f -> "Turun Pelan"
            else -> "Dorong Naik"
        }
        ExerciseMotionType.PLANK -> if (t < 0.5f) "Tahan Lurus" else "Napas Teratur"
        ExerciseMotionType.SQUAT, ExerciseMotionType.GOBLET_SQUAT -> when {
            t < 0.25f -> "Posisi Awal"
            t < 0.75f -> "Turun Jongkok"
            else -> "Berdiri Dorong"
        }
        ExerciseMotionType.GLUTE_BRIDGE -> when {
            t < 0.25f -> "Posisi Awal"
            t < 0.75f -> "Angkat Pinggul"
            else -> "Tahan Puncak"
        }
        ExerciseMotionType.BIRD_DOG -> when {
            cycle < 0.14f || (cycle in 0.45f..0.60f) || cycle > 0.90f -> "Posisi Netral"
            cycle < 0.45f -> "Rentang Sisi 1"
            else -> "Ganti Sisi"
        }
        ExerciseMotionType.OVERHEAD_PRESS -> when {
            t < 0.25f -> "Posisi Bahu"
            t < 0.75f -> "Dorong ke Atas"
            else -> "Tahan Stabil"
        }
        ExerciseMotionType.BENT_OVER_ROW -> when {
            t < 0.25f -> "Lengan Lurus"
            t < 0.75f -> "Tarik ke Pinggang"
            else -> "Kunci Punggung"
        }
        ExerciseMotionType.BICEP_CURL -> when {
            t < 0.25f -> "Lengan Lurus"
            t < 0.75f -> "Tekuk Siku"
            else -> "Turun Pelan"
        }
        ExerciseMotionType.LUNGE -> when {
            t < 0.25f -> "Posisi Awal"
            t < 0.75f -> "Langkah & Turun"
            else -> "Dorong Berdiri"
        }
        ExerciseMotionType.FLOOR_PRESS -> when {
            t < 0.25f -> "Siku di Lantai"
            t < 0.75f -> "Dorong ke Atas"
            else -> "Turun Lembut"
        }
        ExerciseMotionType.ROMANIAN_DEADLIFT -> when {
            t < 0.25f -> "Berdiri Tegak"
            t < 0.75f -> "Dorong Pinggul"
            else -> "Naik Kencang"
        }
        ExerciseMotionType.RUSSIAN_TWIST -> when {
            cycle < 0.5f -> "Putar Kanan"
            else -> "Putar Kiri"
        }
        ExerciseMotionType.CALF_RAISE -> when {
            t < 0.25f -> "Tumit Menapak"
            t < 0.75f -> "Angkat Tumit"
            else -> "Tahan Puncak"
        }
    }
}

@Composable
fun ExerciseAnimationCanvas(
    exerciseName: String,
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val charcoalColor = MaterialTheme.colorScheme.onSurface
    val sageColor = extendedColors.idealAccent
    val sageSoftColor = extendedColors.idealBorder
    val terracottaColor = extendedColors.lightAccent
    val matColor = extendedColors.cardBorder
    val backgroundColor = MaterialTheme.colorScheme.background

    val motionType = remember(exerciseName) { resolveMotionType(exerciseName) }

    val transition = rememberInfiniteTransition(label = "exercise_motion_transition")

    // Smooth 2.4-second back-and-forth movement (1200ms down/up each way = 2.4s full cycle)
    val motionProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "motion_progress"
    )

    // Continuous 2.8-second loop for alternating-side motions (Bird-Dog, Russian Twist)
    val cycleProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycle_progress"
    )

    val phaseText = phaseLabelFor(motionType, motionProgress, cycleProgress)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(192.dp)
            .semantics {
                contentDescription = "Animasi ilustrasi gerakan $exerciseName: $phaseText"
            }
            .testTag("exercise_animation_canvas"),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, extendedColors.cardBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                val stroke = (size.minDimension * 0.044f).coerceIn(5.dp.toPx(), 8.5.dp.toPx())
                val backStroke = stroke * 0.88f
                val headRadius = (size.minDimension * 0.072f).coerceIn(9.dp.toPx(), 14.dp.toPx())
                val groundY = size.height * 0.85f

                // Draw serene exercise mat / floor line
                drawLine(
                    color = matColor,
                    start = Offset(size.width * 0.10f, groundY),
                    end = Offset(size.width * 0.90f, groundY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val palette = FigurePalette(
                    charcoal = charcoalColor,
                    sage = sageColor,
                    sageSoft = sageSoftColor,
                    terracotta = terracottaColor,
                    stroke = stroke,
                    backStroke = backStroke,
                    headRadius = headRadius,
                    groundY = groundY
                )

                when (motionType) {
                    ExerciseMotionType.PUSH_UP -> drawPushUp(motionProgress, palette)
                    ExerciseMotionType.PLANK -> drawPlank(motionProgress, palette)
                    ExerciseMotionType.SQUAT -> drawSquat(motionProgress, withDumbbell = false, palette)
                    ExerciseMotionType.GOBLET_SQUAT -> drawSquat(motionProgress, withDumbbell = true, palette)
                    ExerciseMotionType.GLUTE_BRIDGE -> drawGluteBridge(motionProgress, palette)
                    ExerciseMotionType.BIRD_DOG -> drawBirdDog(cycleProgress, palette)
                    ExerciseMotionType.OVERHEAD_PRESS -> drawOverheadPress(motionProgress, palette)
                    ExerciseMotionType.BENT_OVER_ROW -> drawBentOverRow(motionProgress, palette)
                    ExerciseMotionType.BICEP_CURL -> drawBicepCurl(motionProgress, palette)
                    ExerciseMotionType.LUNGE -> drawLunge(motionProgress, palette)
                    ExerciseMotionType.FLOOR_PRESS -> drawFloorPress(motionProgress, palette)
                    ExerciseMotionType.ROMANIAN_DEADLIFT -> drawRomanianDeadlift(motionProgress, palette)
                    ExerciseMotionType.RUSSIAN_TWIST -> drawRussianTwist(cycleProgress, palette)
                    ExerciseMotionType.CALF_RAISE -> drawCalfRaise(motionProgress, palette)
                }
            }

            // Dynamic Phase Pill in the top-left corner
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, extendedColors.idealBorder),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .testTag("animation_phase_label")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (motionProgress > 0.5f) extendedColors.idealAccent else extendedColors.lightAccent
                            )
                    )
                    Text(
                        text = phaseText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private data class FigurePalette(
    val charcoal: Color,
    val sage: Color,
    val sageSoft: Color,
    val terracotta: Color,
    val stroke: Float,
    val backStroke: Float,
    val headRadius: Float,
    val groundY: Float
)

private fun lerpF(start: Float, end: Float, t: Float): Float = start + (end - start) * t

private fun lerpPt(a: Offset, b: Offset, t: Float): Offset =
    Offset(lerpF(a.x, b.x, t), lerpF(a.y, b.y, t))

private fun DrawScope.drawLimb(
    start: Offset,
    end: Offset,
    color: Color,
    width: Float
) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = width,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawHead(center: Offset, p: FigurePalette) {
    drawCircle(
        color = p.charcoal,
        radius = p.headRadius,
        center = center
    )
}

private fun DrawScope.drawDumbbell(center: Offset, p: FigurePalette, horizontal: Boolean = true) {
    val barHalf = p.stroke * 1.15f
    val plateRadius = p.stroke * 0.72f
    if (horizontal) {
        drawLine(
            color = p.charcoal,
            start = Offset(center.x - barHalf, center.y),
            end = Offset(center.x + barHalf, center.y),
            strokeWidth = p.stroke * 0.55f,
            cap = StrokeCap.Round
        )
        drawCircle(color = p.terracotta, radius = plateRadius, center = Offset(center.x - barHalf, center.y))
        drawCircle(color = p.terracotta, radius = plateRadius, center = Offset(center.x + barHalf, center.y))
    } else {
        drawLine(
            color = p.charcoal,
            start = Offset(center.x, center.y - barHalf),
            end = Offset(center.x, center.y + barHalf),
            strokeWidth = p.stroke * 0.55f,
            cap = StrokeCap.Round
        )
        drawCircle(color = p.terracotta, radius = plateRadius, center = Offset(center.x, center.y - barHalf))
        drawCircle(color = p.terracotta, radius = plateRadius, center = Offset(center.x, center.y + barHalf))
    }
}

private fun DrawScope.pt(nx: Float, ny: Float): Offset = Offset(size.width * nx, size.height * ny)

// 1. Push-up (naik-turun)
private fun DrawScope.drawPushUp(t: Float, p: FigurePalette) {
    val foot = pt(0.22f, 0.83f)
    val hand = pt(0.71f, 0.83f)

    val shoulder = lerpPt(pt(0.67f, 0.49f), pt(0.69f, 0.69f), t)
    val hip = lerpPt(pt(0.45f, 0.65f), pt(0.45f, 0.75f), t)
    val elbow = lerpPt(pt(0.68f, 0.66f), pt(0.58f, 0.71f), t)
    val head = lerpPt(pt(0.76f, 0.43f), pt(0.79f, 0.64f), t)

    // Motion arrow hint
    val arrowY = lerpF(size.height * 0.34f, size.height * 0.52f, t)
    drawCircle(color = p.sageSoft, radius = 3.5.dp.toPx(), center = Offset(size.width * 0.67f, arrowY))

    // Legs & Torso
    drawLimb(foot, hip, p.charcoal, p.stroke)
    drawLimb(hip, shoulder, p.sage, p.stroke)
    // Working arm
    drawLimb(shoulder, elbow, p.charcoal, p.stroke)
    drawLimb(elbow, hand, p.charcoal, p.stroke)
    drawHead(head, p)
}

// 2. Plank (posisi lurus menahan + napas stabil)
private fun DrawScope.drawPlank(t: Float, p: FigurePalette) {
    val breath = sin(t * PI).toFloat()
    val foot = pt(0.20f, 0.83f)
    val elbow = pt(0.68f, 0.83f)
    val hand = pt(0.77f, 0.83f)

    val hip = pt(0.45f, 0.63f - breath * 0.015f)
    val shoulder = pt(0.68f, 0.57f - breath * 0.015f)
    val head = pt(0.79f, 0.54f - breath * 0.015f)

    // Straight alignment dashed guide line
    drawLine(
        color = p.sageSoft,
        start = pt(0.20f, 0.73f),
        end = pt(0.78f, 0.52f),
        strokeWidth = 1.6.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    )

    // Core stability pulse ring around midsection
    drawCircle(
        color = p.sage.copy(alpha = 0.22f * (1f - breath * 0.4f)),
        radius = (11.dp.toPx() + breath * 7.dp.toPx()),
        center = lerpPt(hip, shoulder, 0.45f),
        style = Stroke(width = 2.dp.toPx())
    )

    drawLimb(foot, hip, p.charcoal, p.stroke)
    drawLimb(hip, shoulder, p.sage, p.stroke)
    drawLimb(shoulder, elbow, p.charcoal, p.stroke)
    drawLimb(elbow, hand, p.charcoal, p.stroke)
    drawHead(head, p)
}

// 3. Squat & Goblet Squat (jongkok-berdiri)
private fun DrawScope.drawSquat(t: Float, withDumbbell: Boolean, p: FigurePalette) {
    val foot = pt(0.50f, 0.83f)
    val knee = lerpPt(pt(0.51f, 0.64f), pt(0.58f, 0.66f), t)
    val hip = lerpPt(pt(0.49f, 0.47f), pt(0.39f, 0.66f), t)
    val shoulder = lerpPt(pt(0.50f, 0.26f), pt(0.48f, 0.43f), t)
    val head = lerpPt(pt(0.51f, 0.16f), pt(0.51f, 0.33f), t)

    val elbow = if (withDumbbell) {
        lerpPt(pt(0.53f, 0.38f), pt(0.54f, 0.55f), t)
    } else {
        lerpPt(pt(0.57f, 0.36f), pt(0.61f, 0.45f), t)
    }
    val hand = if (withDumbbell) {
        lerpPt(pt(0.58f, 0.32f), pt(0.59f, 0.49f), t)
    } else {
        lerpPt(pt(0.60f, 0.44f), pt(0.71f, 0.43f), t)
    }

    // Working legs in sage
    drawLimb(foot, knee, p.sage, p.stroke)
    drawLimb(knee, hip, p.sage, p.stroke)
    // Torso
    drawLimb(hip, shoulder, p.charcoal, p.stroke)
    // Arm
    drawLimb(shoulder, elbow, p.charcoal, p.backStroke)
    drawLimb(elbow, hand, p.charcoal, p.backStroke)
    if (withDumbbell) {
        drawDumbbell(hand, p, horizontal = false)
    }
    drawHead(head, p)
}

// 4. Glute Bridge (pinggul naik-turun)
private fun DrawScope.drawGluteBridge(t: Float, p: FigurePalette) {
    val head = pt(0.24f, 0.77f)
    val shoulder = pt(0.34f, 0.81f)
    val foot = pt(0.74f, 0.83f)
    val knee = lerpPt(pt(0.67f, 0.59f), pt(0.68f, 0.53f), t)
    val hip = lerpPt(pt(0.53f, 0.81f), pt(0.52f, 0.62f), t)
    val hand = pt(0.55f, 0.83f)

    // Subtle lift glow under hip at peak
    if (t > 0.2f) {
        drawCircle(
            color = p.sage.copy(alpha = 0.18f * t),
            radius = 12.dp.toPx(),
            center = hip
        )
    }

    // Resting arm along mat
    drawLimb(shoulder, hand, p.sageSoft, p.backStroke)
    // Working bridge: shoulder -> hip -> knee -> foot
    drawLimb(shoulder, hip, p.sage, p.stroke)
    drawLimb(hip, knee, p.sage, p.stroke)
    drawLimb(knee, foot, p.charcoal, p.stroke)
    drawHead(head, p)
}

// 5. Bird-Dog (tangan & kaki bergantian)
private fun DrawScope.drawBirdDog(cycle: Float, p: FigurePalette) {
    // 0.0..0.5 -> Side 1 extends; 0.5..1.0 -> Side 2 extends
    val isSideOne = cycle < 0.5f
    val localPhase = if (isSideOne) cycle * 2f else (cycle - 0.5f) * 2f
    val ext = sin(localPhase * PI).toFloat().coerceIn(0f, 1f)

    val hip = pt(0.41f, 0.54f)
    val shoulder = pt(0.61f, 0.52f)
    val head = pt(0.71f, 0.47f)

    val baseKnee = pt(0.41f, 0.82f)
    val baseFoot = pt(0.30f, 0.83f)
    val baseHand = pt(0.62f, 0.83f)

    val movingArmColor = if (isSideOne) p.sage else p.terracotta
    val movingLegColor = if (isSideOne) p.sage else p.terracotta

    // Supporting limbs (planted)
    drawLimb(hip, baseKnee, p.charcoal.copy(alpha = 0.45f), p.backStroke)
    drawLimb(baseKnee, baseFoot, p.charcoal.copy(alpha = 0.45f), p.backStroke)
    drawLimb(shoulder, baseHand, p.charcoal.copy(alpha = 0.45f), p.backStroke)

    // Extending leg (from tabletop knee-down to straight back)
    val extKnee = lerpPt(pt(0.43f, 0.82f), pt(0.28f, 0.55f), ext)
    val extFoot = lerpPt(pt(0.32f, 0.83f), pt(0.15f, 0.55f), ext)
    drawLimb(hip, extKnee, movingLegColor, p.stroke)
    drawLimb(extKnee, extFoot, movingLegColor, p.stroke)

    // Extending arm (from floor up straight forward)
    val extHand = lerpPt(pt(0.63f, 0.83f), pt(0.84f, 0.50f), ext)
    drawLimb(shoulder, extHand, movingArmColor, p.stroke)

    // Stable torso + head
    drawLimb(hip, shoulder, p.charcoal, p.stroke)
    drawHead(head, p)
}

// 6. Overhead Press (angkat dumbbell ke atas)
private fun DrawScope.drawOverheadPress(t: Float, p: FigurePalette) {
    val head = pt(0.50f, 0.25f)
    val shoulderCenter = pt(0.50f, 0.36f)
    val leftShoulder = pt(0.43f, 0.37f)
    val rightShoulder = pt(0.57f, 0.37f)
    val hip = pt(0.50f, 0.58f)
    val leftFoot = pt(0.44f, 0.83f)
    val rightFoot = pt(0.56f, 0.83f)

    val leftElbow = lerpPt(pt(0.35f, 0.44f), pt(0.41f, 0.25f), t)
    val rightElbow = lerpPt(pt(0.65f, 0.44f), pt(0.59f, 0.25f), t)
    val leftHand = lerpPt(pt(0.36f, 0.31f), pt(0.43f, 0.13f), t)
    val rightHand = lerpPt(pt(0.64f, 0.31f), pt(0.57f, 0.13f), t)

    // Standing base
    drawLimb(hip, leftFoot, p.charcoal, p.stroke)
    drawLimb(hip, rightFoot, p.charcoal, p.stroke)
    drawLimb(hip, shoulderCenter, p.charcoal, p.stroke)
    drawLimb(leftShoulder, rightShoulder, p.charcoal, p.stroke)

    // Working pressing arms in sage + dumbbells
    drawLimb(leftShoulder, leftElbow, p.sage, p.stroke)
    drawLimb(leftElbow, leftHand, p.sage, p.stroke)
    drawLimb(rightShoulder, rightElbow, p.sage, p.stroke)
    drawLimb(rightElbow, rightHand, p.sage, p.stroke)

    drawDumbbell(leftHand, p, horizontal = true)
    drawDumbbell(rightHand, p, horizontal = true)
    drawHead(head, p)
}

// 7. Bent-Over Row / Dumbbell Row (tarik dumbbell ke pinggang)
private fun DrawScope.drawBentOverRow(t: Float, p: FigurePalette) {
    val foot = pt(0.42f, 0.83f)
    val knee = pt(0.46f, 0.65f)
    val hip = pt(0.39f, 0.49f)
    val shoulder = pt(0.59f, 0.35f)
    val head = pt(0.68f, 0.29f)

    val elbow = lerpPt(pt(0.59f, 0.52f), pt(0.49f, 0.39f), t)
    val hand = lerpPt(pt(0.59f, 0.68f), pt(0.52f, 0.51f), t)

    // Stable hinged legs & flat back
    drawLimb(foot, knee, p.charcoal, p.stroke)
    drawLimb(knee, hip, p.charcoal, p.stroke)
    drawLimb(hip, shoulder, p.charcoal, p.stroke)

    // Working pulling arm in sage + dumbbell
    drawLimb(shoulder, elbow, p.sage, p.stroke)
    drawLimb(elbow, hand, p.sage, p.stroke)
    drawDumbbell(hand, p, horizontal = true)
    drawHead(head, p)
}

// 8. Bicep Curl (tekuk siku)
private fun DrawScope.drawBicepCurl(t: Float, p: FigurePalette) {
    val foot = pt(0.48f, 0.83f)
    val knee = pt(0.48f, 0.65f)
    val hip = pt(0.48f, 0.49f)
    val shoulder = pt(0.48f, 0.27f)
    val head = pt(0.49f, 0.16f)
    val elbow = pt(0.48f, 0.46f)

    // Forearm rotates smoothly around locked elbow from straight down to shoulder
    val angleRad = lerpF(PI.toFloat() * 0.48f, -PI.toFloat() * 0.36f, t)
    val forearmLen = size.minDimension * 0.24f
    val hand = Offset(
        x = elbow.x + cos(angleRad) * forearmLen,
        y = elbow.y + sin(angleRad) * forearmLen
    )

    drawLimb(foot, knee, p.charcoal, p.stroke)
    drawLimb(knee, hip, p.charcoal, p.stroke)
    drawLimb(hip, shoulder, p.charcoal, p.stroke)
    drawLimb(shoulder, elbow, p.sage, p.stroke)
    drawLimb(elbow, hand, p.sage, p.stroke)
    drawDumbbell(hand, p, horizontal = false)
    drawHead(head, p)
}

// 9. Reverse Lunge (langkah ke belakang & turun)
private fun DrawScope.drawLunge(t: Float, p: FigurePalette) {
    val frontFoot = pt(0.58f, 0.83f)
    val frontKnee = lerpPt(pt(0.57f, 0.64f), pt(0.60f, 0.65f), t)
    val hip = lerpPt(pt(0.54f, 0.48f), pt(0.47f, 0.63f), t)
    val shoulder = lerpPt(pt(0.54f, 0.27f), pt(0.48f, 0.42f), t)
    val head = lerpPt(pt(0.55f, 0.17f), pt(0.49f, 0.32f), t)

    val backKnee = lerpPt(pt(0.53f, 0.64f), pt(0.42f, 0.79f), t)
    val backFoot = lerpPt(pt(0.52f, 0.83f), pt(0.30f, 0.83f), t)
    val hand = lerpPt(pt(0.54f, 0.51f), pt(0.48f, 0.64f), t)

    // Back leg
    drawLimb(hip, backKnee, p.charcoal.copy(alpha = 0.6f), p.backStroke)
    drawLimb(backKnee, backFoot, p.charcoal.copy(alpha = 0.6f), p.backStroke)

    // Front working leg in sage
    drawLimb(hip, frontKnee, p.sage, p.stroke)
    drawLimb(frontKnee, frontFoot, p.sage, p.stroke)

    // Torso & arm holding dumbbell at side
    drawLimb(hip, shoulder, p.charcoal, p.stroke)
    drawLimb(shoulder, hand, p.charcoal, p.backStroke)
    drawDumbbell(hand, p, horizontal = true)
    drawHead(head, p)
}

// 10. Dumbbell Floor Press
private fun DrawScope.drawFloorPress(t: Float, p: FigurePalette) {
    val head = pt(0.24f, 0.77f)
    val shoulder = pt(0.35f, 0.81f)
    val hip = pt(0.54f, 0.81f)
    val knee = pt(0.66f, 0.60f)
    val foot = pt(0.75f, 0.83f)

    val elbow = lerpPt(pt(0.37f, 0.81f), pt(0.36f, 0.61f), t)
    val hand = lerpPt(pt(0.37f, 0.63f), pt(0.36f, 0.42f), t)

    drawLimb(shoulder, hip, p.charcoal, p.stroke)
    drawLimb(hip, knee, p.charcoal, p.stroke)
    drawLimb(knee, foot, p.charcoal, p.stroke)

    drawLimb(shoulder, elbow, p.sage, p.stroke)
    drawLimb(elbow, hand, p.sage, p.stroke)
    drawDumbbell(hand, p, horizontal = true)
    drawHead(head, p)
}

// 11. Romanian Deadlift (hip hinge)
private fun DrawScope.drawRomanianDeadlift(t: Float, p: FigurePalette) {
    val foot = pt(0.48f, 0.83f)
    val knee = lerpPt(pt(0.49f, 0.64f), pt(0.51f, 0.66f), t)
    val hip = lerpPt(pt(0.48f, 0.47f), pt(0.38f, 0.50f), t)
    val shoulder = lerpPt(pt(0.49f, 0.25f), pt(0.62f, 0.45f), t)
    val head = lerpPt(pt(0.50f, 0.15f), pt(0.71f, 0.42f), t)
    val hand = lerpPt(pt(0.51f, 0.52f), pt(0.56f, 0.70f), t)

    drawLimb(foot, knee, p.charcoal, p.stroke)
    drawLimb(knee, hip, p.sage, p.stroke)
    drawLimb(hip, shoulder, p.sage, p.stroke)
    drawLimb(shoulder, hand, p.charcoal, p.backStroke)
    drawDumbbell(hand, p, horizontal = true)
    drawHead(head, p)
}

// 12. Russian Twist (duduk putar kiri-kanan)
private fun DrawScope.drawRussianTwist(cycle: Float, p: FigurePalette) {
    val swing = sin(cycle * 2.0 * PI).toFloat() // -1f..1f
    val hip = pt(0.48f, 0.79f)
    val knee = pt(0.63f, 0.62f)
    val foot = pt(0.74f, 0.83f)
    val shoulder = pt(0.38f, 0.50f)
    val head = pt(0.33f, 0.39f)

    val hand = Offset(
        x = size.width * (0.50f + swing * 0.12f),
        y = size.height * (0.58f + abs(swing) * 0.04f)
    )

    drawLimb(hip, knee, p.charcoal, p.stroke)
    drawLimb(knee, foot, p.charcoal, p.stroke)
    drawLimb(hip, shoulder, p.charcoal, p.stroke)
    drawLimb(shoulder, hand, p.sage, p.stroke)
    drawDumbbell(hand, p, horizontal = true)
    drawHead(head, p)
}

// 13. Calf Raise (angkat tumit)
private fun DrawScope.drawCalfRaise(t: Float, p: FigurePalette) {
    val lift = t * 0.065f
    val toe = pt(0.52f, 0.83f)
    val heel = pt(0.46f, 0.83f - lift * 0.85f)
    val ankle = pt(0.49f, 0.80f - lift)
    val knee = pt(0.49f, 0.62f - lift)
    val hip = pt(0.49f, 0.45f - lift)
    val shoulder = pt(0.49f, 0.25f - lift)
    val head = pt(0.49f, 0.15f - lift)
    val hand = pt(0.51f, 0.50f - lift)

    // Foot & calf highlight in sage
    drawLimb(toe, heel, p.sage, p.backStroke)
    drawLimb(ankle, knee, p.sage, p.stroke)
    drawLimb(knee, hip, p.charcoal, p.stroke)
    drawLimb(hip, shoulder, p.charcoal, p.stroke)
    drawLimb(shoulder, hand, p.charcoal, p.backStroke)
    drawDumbbell(hand, p, horizontal = true)
    drawHead(head, p)
}
