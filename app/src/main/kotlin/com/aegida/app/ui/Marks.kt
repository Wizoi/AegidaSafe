package com.aegida.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aegida.app.ui.theme.Teal
import com.aegida.app.ui.theme.amberColor
import com.aegida.app.ui.theme.goodColor
import com.aegida.core.text.Texts

/** The Aegida shield (a rounded shield with a check), drawn in code in the brand colours. */
@Composable
fun AegidaShield(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).semantics { contentDescription = Texts.APP_NAME }) {
        val s = this.size.minDimension
        val shield = Path().apply {
            moveTo(ShieldTop, s)
            lineTo(ShieldRightShoulder, s)
            lineTo(ShieldRightSide, s)
            quadraticTo(ShieldRightCurve, ShieldBottom, s)
            quadraticTo(ShieldLeftCurve, ShieldLeftSide, s)
            lineTo(ShieldLeftShoulder, s)
            close()
        }
        drawPath(shield, Teal)
        drawPath(
            Path().apply { moveTo(ShieldTickStart, s); lineTo(ShieldTickBend, s); lineTo(ShieldTickEnd, s) },
            Color.White, style = Stroke(width = s * SHIELD_TICK_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

enum class Mark { Good, NeedsTap, Unknown }

/** The big round mark on Home: a check, an exclamation mark, or a dash. Colour is never the only signal. */
@Composable
fun StatusMark(mark: Mark, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    val colour = when (mark) {
        Mark.Good -> goodColor()
        Mark.NeedsTap, Mark.Unknown -> amberColor()
    }
    val description = when (mark) {
        Mark.Good -> Texts.Home.ALL_GOOD
        Mark.NeedsTap -> Texts.Home.NEEDS_ONE
        Mark.Unknown -> Texts.Home.COULDNT_CHECK
    }
    Canvas(modifier = modifier.size(size).semantics { contentDescription = description }) {
        drawCircle(colour)
        val s = this.size.minDimension
        val stroke = Stroke(width = s * MARK_STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (mark) {
            Mark.Good -> drawPath(
                Path().apply { moveTo(MarkTickStart, s); lineTo(MarkTickBend, s); lineTo(MarkTickEnd, s) },
                Color.White, style = stroke,
            )
            Mark.NeedsTap -> {
                drawLine(Color.White, MarkBangTop * s, MarkBangBottom * s, stroke.width, StrokeCap.Round)
                drawCircle(Color.White, s * MARK_BANG_DOT_RADIUS, MarkBangDot * s)
            }
            Mark.Unknown -> drawLine(Color.White, MarkDashStart * s, MarkDashEnd * s, stroke.width, StrokeCap.Round)
        }
    }
}

// Shapes are drawn in a unit square and scaled by the size of the canvas. Each point is named by what it is part of.

private const val SHIELD_TICK_WIDTH = 0.08f
private val ShieldTop = Offset(0.5f, 0.06f)
private val ShieldRightShoulder = Offset(0.88f, 0.2f)
private val ShieldRightSide = Offset(0.88f, 0.5f)
private val ShieldRightCurve = Offset(0.88f, 0.82f)
private val ShieldBottom = Offset(0.5f, 0.96f)
private val ShieldLeftCurve = Offset(0.12f, 0.82f)
private val ShieldLeftSide = Offset(0.12f, 0.5f)
private val ShieldLeftShoulder = Offset(0.12f, 0.2f)
private val ShieldTickStart = Offset(0.32f, 0.5f)
private val ShieldTickBend = Offset(0.45f, 0.63f)
private val ShieldTickEnd = Offset(0.69f, 0.35f)

private const val MARK_STROKE_WIDTH = 0.09f
private const val MARK_BANG_DOT_RADIUS = 0.055f
private val MarkTickStart = Offset(0.27f, 0.52f)
private val MarkTickBend = Offset(0.43f, 0.68f)
private val MarkTickEnd = Offset(0.74f, 0.34f)
private val MarkBangTop = Offset(0.5f, 0.25f)
private val MarkBangBottom = Offset(0.5f, 0.55f)
private val MarkBangDot = Offset(0.5f, 0.72f)
private val MarkDashStart = Offset(0.3f, 0.5f)
private val MarkDashEnd = Offset(0.7f, 0.5f)

private fun Path.moveTo(point: Offset, scale: Float) = moveTo(point.x * scale, point.y * scale)
private fun Path.lineTo(point: Offset, scale: Float) = lineTo(point.x * scale, point.y * scale)
private fun Path.quadraticTo(control: Offset, end: Offset, scale: Float) =
    quadraticBezierTo(control.x * scale, control.y * scale, end.x * scale, end.y * scale)
