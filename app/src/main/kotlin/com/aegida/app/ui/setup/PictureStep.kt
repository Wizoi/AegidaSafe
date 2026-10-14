package com.aegida.app.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aegida.app.ui.Body
import com.aegida.app.ui.BigButton
import com.aegida.app.ui.PictureTile
import com.aegida.core.model.SafetyPicture
import com.aegida.core.text.Texts

/** The ten pictures in a grid. Used in setup and when she changes her picture later. */
@Composable
fun PictureStep(
    position: StepPosition,
    chosen: SafetyPicture?,
    onChoose: (SafetyPicture) -> Unit,
    onContinue: () -> Unit,
    buttonText: String = Texts.Common.CONTINUE,
) {
    var picked by rememberSaveable { mutableStateOf(chosen?.id) }
    StepScreen(position.copy(pictureId = picked ?: position.pictureId), Texts.Setup.PICTURE_TITLE) {
        Body(Texts.Setup.PICTURE_BODY, centered = false)
        PictureGrid(picked) { picture -> picked = picture.id; onChoose(picture) }
        BigButton(buttonText, onContinue, enabled = picked != null)
    }
}

/**
 * The ten pictures, three across, sized from the screen so the Continue button stays on the first screen (Kevin, Oct 9). At
 * very large text three across would cut the labels, so it is two across there and the screen scrolls, as it always did.
 */
@Composable
private fun PictureGrid(picked: String?, onPick: (SafetyPicture) -> Unit) {
    val config = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val perRow = if (fontScale > LARGE_TEXT_SCALE) COLUMNS_LARGE_TEXT else COLUMNS_NORMAL
    val gap = GRID_GAP
    val rows = (SafetyPicture.entries.size + perRow - 1) / perRow
    val label = LABEL_HEIGHT * fontScale
    // Across: the width left after the side padding. Down: the screen less everything above and below the grid.
    val acrossLimit = (config.screenWidthDp.dp - SIDE_PADDING_BOTH - gap * (perRow - 1)) / perRow
    val room = config.screenHeightDp.dp - (CHROME + TEXT_CHROME * fontScale)
    val downLimit = (room - gap * (rows - 1)) / rows - label
    // Large text: the screen scrolls anyway, so keep the big tiles she had before (two across, labels fit).
    val tile = if (perRow == COLUMNS_LARGE_TEXT) LARGE_TEXT_TILE else minOf(acrossLimit, downLimit, MAX_TILE).coerceAtLeast(MIN_TILE)
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        SafetyPicture.entries.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { picture ->
                    val isPicked = picked == picture.id
                    Column(
                        Modifier.weight(1f).clickable { onPick(picture) }
                            .semantics { contentDescription = picture.label; selected = isPicked },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PictureTile(picture, tile, selected = isPicked)
                        Text(picture.label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val LARGE_TEXT_SCALE = 1.3f
private const val COLUMNS_NORMAL = 3
private const val COLUMNS_LARGE_TEXT = 2
private val GRID_GAP = 12.dp
private val LABEL_HEIGHT = 26.dp
private val SIDE_PADDING_BOTH = 48.dp // the 24 dp padding on each side of a screen
private val LARGE_TEXT_TILE = 96.dp
private val MAX_TILE = 112.dp
private val MIN_TILE = 56.dp

// What sits above and below the grid on this step: the header, step label, title, text, Continue and Back, and the status and
// navigation bars. The part that is text grows with the font size.
private val CHROME = 150.dp
private val TEXT_CHROME = 250.dp
