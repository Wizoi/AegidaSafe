package com.aegida.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aegida.core.text.Texts

internal enum class Tab(val label: String) {
    Home(Texts.Nav.HOME), History(Texts.Nav.HISTORY), Settings(Texts.Nav.SETTINGS)
}

/** Three plain icons drawn in code: a house, a clock, and three sliders. Points are in a unit square, scaled by the canvas. */
@Composable
internal fun TabIcon(tab: Tab) {
    val colour = LocalContentColor.current
    Canvas(Modifier.size(ICON_SIZE)) {
        val s = size.minDimension
        val line = Stroke(width = s * ICON_STROKE, cap = StrokeCap.Round)
        when (tab) {
            Tab.Home -> drawPath(Path().apply { polyline(HouseRoof, s); polyline(HouseWalls, s) }, colour, style = line)
            Tab.History -> drawClock(colour, s, line)
            Tab.Settings -> drawSliders(colour, s, line.width)
        }
    }
}

private fun DrawScope.drawClock(colour: androidx.compose.ui.graphics.Color, s: Float, line: Stroke) {
    drawCircle(colour, s * CLOCK_RADIUS, center, style = line)
    drawLine(colour, center, ClockHourTip * s, line.width, StrokeCap.Round)
    drawLine(colour, center, ClockMinuteTip * s, line.width, StrokeCap.Round)
}

private fun DrawScope.drawSliders(colour: androidx.compose.ui.graphics.Color, s: Float, lineWidth: Float) {
    SliderHeights.forEachIndexed { i, y ->
        drawLine(colour, Offset(s * SLIDER_LEFT, s * y), Offset(s * SLIDER_RIGHT, s * y), lineWidth, StrokeCap.Round)
        drawCircle(colour, s * KNOB_RADIUS, Offset(s * SliderKnobAcross[i], s * y))
    }
}

private fun Path.polyline(points: List<Offset>, scale: Float) {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * scale, p.y * scale) else lineTo(p.x * scale, p.y * scale) }
}

private val ICON_SIZE = 28.dp
private const val ICON_STROKE = 0.09f

private val HouseRoof = listOf(Offset(0.12f, 0.48f), Offset(0.5f, 0.14f), Offset(0.88f, 0.48f))
private val HouseWalls = listOf(Offset(0.22f, 0.42f), Offset(0.22f, 0.86f), Offset(0.78f, 0.86f), Offset(0.78f, 0.42f))

private const val CLOCK_RADIUS = 0.4f
private val ClockHourTip = Offset(0.5f, 0.24f)
private val ClockMinuteTip = Offset(0.68f, 0.58f)

private const val SLIDER_LEFT = 0.12f
private const val SLIDER_RIGHT = 0.88f
private const val KNOB_RADIUS = 0.09f
private val SliderHeights = listOf(0.25f, 0.5f, 0.75f)
private val SliderKnobAcross = listOf(0.65f, 0.35f, 0.6f)
