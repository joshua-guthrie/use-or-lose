package com.westmouthbay.fedleave.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The four 9 x 9 dp schedule glyphs from the [date marker chapter](../../../../specification/chapters/01-calendar/01-04-date-markers.md),
 * drawn with Canvas and tinted by the active theme. Table order: holiday
 * star, payday coin, pay-period-end flag, leave-year-end checked calendar.
 */

private val GLYPH_SIZE = 9.dp

@Composable
fun HolidayStarGlyph(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier.size(GLYPH_SIZE)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outer = size.width / 2f
        val inner = outer * 0.38f
        val path = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val x = center.x + radius * kotlin.math.cos(angle).toFloat()
            val y = center.y + radius * kotlin.math.sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}

@Composable
fun PaydayCoinGlyph(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier.size(GLYPH_SIZE)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f - size.width * 0.12f
        val stroke = size.width * 0.16f
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = stroke))
        drawLine(
            color = color,
            start = Offset(center.x, size.height * 0.18f),
            end = Offset(center.x, size.height * 0.82f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun PayPeriodEndFlagGlyph(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier.size(GLYPH_SIZE)) {
        val stroke = size.width * 0.16f
        val poleX = size.width * 0.24f
        drawLine(
            color = color,
            start = Offset(poleX, size.height * 0.12f),
            end = Offset(poleX, size.height * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val flag = Path().apply {
            moveTo(poleX, size.height * 0.14f)
            lineTo(size.width * 0.86f, size.height * 0.32f)
            lineTo(poleX, size.height * 0.50f)
            close()
        }
        drawPath(flag, color)
    }
}

@Composable
fun LeaveYearEndGlyph(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier.size(GLYPH_SIZE)) {
        val stroke = size.width * 0.14f
        val left = size.width * 0.12f
        val top = size.height * 0.16f
        val right = size.width * 0.88f
        val bottom = size.height * 0.84f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = CornerRadius(size.width * 0.14f),
            style = Stroke(width = stroke),
        )
        val check = Path().apply {
            moveTo(size.width * 0.30f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.66f)
            lineTo(size.width * 0.70f, size.height * 0.34f)
        }
        drawPath(
            path = check,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** The marker kinds in table order, used by cells and spoken labels. */
enum class MarkerKind {
    HOLIDAY,
    PAYDAY,
    PERIOD_END,
    YEAR_END,
}

data class DayMarker(
    val kind: MarkerKind,
    val spoken: String,
)
