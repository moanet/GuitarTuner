package com.example.guitartuner.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.guitartuner.ui.theme.Accent
import com.example.guitartuner.ui.theme.Danger
import com.example.guitartuner.ui.theme.DialTick
import com.example.guitartuner.ui.theme.DialTrack
import com.example.guitartuner.ui.theme.Neutral
import com.example.guitartuner.ui.theme.Warn
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 半圆形调音表。指针角度由音分偏差映射：
 * 0 cents = 正上（270°），±50 cents = ±90°（向左偏低 / 向右偏高）。
 * 指针用弹簧动画平滑过渡，不跳变。
 *
 * 色带分区（角度：0=右, 90=下, 180=左, 270=上，顺时针为正向）：
 *  - 绿（±5 cents）：[261°,279°]
 *  - 橙（±5~10 cents）：[252°,261°] ∪ [279°,288°]
 *  - 红（±10~50 cents）：[180°,252°] ∪ [288°,360°]
 */
private const val DEG_PER_CENT = 90f / 50f

@Composable
fun TuningDial(
    cents: Double,
    active: Boolean,
    inTune: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(
        targetValue = cents.coerceIn(-50.0, 50.0).toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tunerNeedle"
    )

    Canvas(modifier = modifier.aspectRatio(2f)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.90f
        val r = min(w / 2f, h) * 0.96f
        val strokeW = 16.dp.toPx()
        val topLeft = Offset(cx - r, cy - r)
        val arcSize = Size(2 * r, 2 * r)

        fun pointOn(radius: Float, angleDeg: Float): Offset {
            val a = angleDeg * (PI.toFloat() / 180f)
            return Offset(cx + radius * cos(a), cy + radius * sin(a))
        }

        // 调准辉光：轨道外圈一层扩散光晕（仅 inTune 时）
        if (inTune) {
            val glowR = r + 12.dp.toPx()
            drawArc(
                color = Accent.copy(alpha = 0.45f), startAngle = 180f, sweepAngle = 180f,
                useCenter = false, topLeft = Offset(cx - glowR, cy - glowR), size = Size(2 * glowR, 2 * glowR),
                style = Stroke(width = strokeW + 10.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Accent.copy(alpha = 0.20f), startAngle = 180f, sweepAngle = 180f,
                useCenter = false, topLeft = Offset(cx - glowR - 8.dp.toPx(), cy - glowR - 8.dp.toPx()),
                size = Size(2 * (glowR + 8.dp.toPx()), 2 * (glowR + 8.dp.toPx())),
                style = Stroke(width = strokeW + 18.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 背景半圆轨道
        drawArc(
            color = DialTrack, startAngle = 180f, sweepAngle = 180f,
            useCenter = false, topLeft = topLeft, size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // 色带分区
        fun zone(start: Float, sweep: Float, color: Color) {
            drawArc(color, start, sweep, useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Butt))
        }
        zone(180f, 72f, Danger)  // 左红 [-50,-10]
        zone(252f, 9f, Warn)     // 左橙 [-10,-5]
        zone(261f, 18f, Accent)  // 绿   [-5, 5]
        zone(279f, 9f, Warn)     // 右橙 [5,10]
        zone(288f, 72f, Danger)  // 右红 [10,50]

        // 刻度（每 1 cent，10 cents 为主刻度）
        for (c in -50..50) {
            val a = 270f + c * DEG_PER_CENT
            val major = c % 10 == 0
            val p1 = pointOn(if (major) r * 0.70f else r * 0.78f, a)
            val p2 = pointOn(r * 0.88f, a)
            drawLine(
                color = DialTick, start = p1, end = p2,
                strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 指针
        val needleAngle = 270f + animated * DEG_PER_CENT
        val tip = pointOn(r * 0.62f, needleAngle)
        val pointerColor = if (active) pointerColor(cents) else Neutral
        drawLine(
            color = pointerColor, start = Offset(cx, cy), end = tip,
            strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round
        )
        drawCircle(color = pointerColor, radius = 9.dp.toPx(), center = Offset(cx, cy))
        drawCircle(color = DialTrack, radius = 3.dp.toPx(), center = Offset(cx, cy))
    }
}

private fun pointerColor(cents: Double): Color = when {
    abs(cents) <= 5.0 -> Accent
    abs(cents) <= 12.0 -> Warn
    else -> Danger
}
