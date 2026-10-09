package com.aegida.app.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aegida.app.R
import com.aegida.app.ui.theme.Teal
import com.aegida.app.ui.theme.amberColor
import com.aegida.app.ui.theme.goodColor
import com.aegida.core.model.SafetyPicture
import com.aegida.core.text.Texts

/** Buttons are at least 64 dp tall and as wide as the screen: one clear thing to press. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

/** One of the ten pictures on its own coloured tile. */
@Composable
fun PictureTile(picture: SafetyPicture, size: Dp, modifier: Modifier = Modifier, selected: Boolean = false) {
    val shape = RoundedCornerShape(size / 4)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colorResource(Pictures.tileColor(picture)))
            .then(
                if (selected) Modifier.border(4.dp, MaterialTheme.colorScheme.outline, shape)
                else Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), shape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Pictures.drawable(picture)),
            contentDescription = null, // the tile's own description is set by the caller
            modifier = Modifier.size(size * 0.72f),
        )
    }
}

/** Her picture and "Aegida" at the top of every genuine screen: a fake one will not have her picture. */
@Composable
fun AegidaHeader(pictureId: String?, modifier: Modifier = Modifier) {
    val picture = SafetyPicture.fromId(pictureId)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (picture != null) {
            PictureTile(picture, 48.dp, Modifier.semantics { contentDescription = Texts.Common.YOUR_PICTURE.format(picture.label) })
        }
        Text(Texts.APP_NAME, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/** A screen: header, then content that scrolls, so large text never pushes a button off the bottom. */
@Composable
fun AegidaScreen(pictureId: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AegidaHeader(pictureId)
            content()
        }
    }
}

/** The Aegida shield (a rounded shield with a check), drawn in code in the brand colours. */
@Composable
fun AegidaShield(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).semantics { contentDescription = Texts.APP_NAME }) {
        val s = this.size.minDimension
        val shield = Path().apply {
            moveTo(s * 0.5f, s * 0.06f)
            lineTo(s * 0.88f, s * 0.2f)
            lineTo(s * 0.88f, s * 0.5f)
            quadraticBezierTo(s * 0.88f, s * 0.82f, s * 0.5f, s * 0.96f)
            quadraticBezierTo(s * 0.12f, s * 0.82f, s * 0.12f, s * 0.5f)
            lineTo(s * 0.12f, s * 0.2f)
            close()
        }
        drawPath(shield, Teal)
        drawPath(
            Path().apply { moveTo(s * 0.32f, s * 0.5f); lineTo(s * 0.45f, s * 0.63f); lineTo(s * 0.69f, s * 0.35f) },
            Color.White, style = Stroke(width = s * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** A finished step: a green tick and the words, so "done" is never only a colour. */
@Composable
fun DoneRow(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = text },
        shape = RoundedCornerShape(16.dp),
        color = goodColor().copy(alpha = 0.18f),
        border = BorderStroke(2.dp, goodColor()),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusMark(Mark.Good, 40.dp)
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

enum class Mark { Good, NeedsTap, Unknown }

/** The big round mark on Home: a check, an exclamation mark, or a dash. Colour is never the only signal. */
@Composable
fun StatusMark(mark: Mark, size: Dp = 88.dp, modifier: Modifier = Modifier) {
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
        val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (mark) {
            Mark.Good -> drawPath(
                Path().apply {
                    moveTo(s * 0.27f, s * 0.52f); lineTo(s * 0.43f, s * 0.68f); lineTo(s * 0.74f, s * 0.34f)
                },
                Color.White, style = stroke,
            )
            Mark.NeedsTap -> {
                drawLine(Color.White, Offset(s * 0.5f, s * 0.25f), Offset(s * 0.5f, s * 0.55f), stroke.width, StrokeCap.Round)
                drawCircle(Color.White, s * 0.055f, Offset(s * 0.5f, s * 0.72f))
            }
            Mark.Unknown -> drawLine(Color.White, Offset(s * 0.3f, s * 0.5f), Offset(s * 0.7f, s * 0.5f), stroke.width, StrokeCap.Round)
        }
    }
}

/** The small dot on a History line: green = handled, amber = still needs her. */
@Composable
fun HandledDot(handled: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(18.dp).clip(CircleShape).background(if (handled) goodColor() else amberColor()))
}

@Composable
fun Gap(width: Dp) = Spacer(Modifier.width(width))
