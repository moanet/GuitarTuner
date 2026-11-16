package com.example.guitartuner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.guitartuner.ui.theme.Accent
import com.example.guitartuner.ui.theme.Neutral
import com.example.guitartuner.ui.theme.Warn

/**
 * 环形稳定度指示：stability ∈ [0,1]，越高越可信。
 * 读数可信度（confidence/稳定度）的辅助视觉，帮助用户判断指针是否可靠。
 */
@Composable
fun StabilityRing(stability: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 5.dp.toPx()
        val d = (size.minDimension - stroke) / 2f
        val topLeft = Offset((size.width - d * 2) / 2f, (size.height - d * 2) / 2f)
        val arcSize = Size(d * 2, d * 2)

        // 底环
        drawArc(
            color = Neutral.copy(alpha = 0.3f), startAngle = 0f, sweepAngle = 360f,
            useCenter = false, topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // 进度环（-90° 起顺时针）
        val sweep = stability.coerceIn(0f, 1f) * 360f
        val color = if (stability > 0.6f) Accent else Warn
        drawArc(
            color = color, startAngle = -90f, sweepAngle = sweep,
            useCenter = false, topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}
