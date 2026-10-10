package com.aegida.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aegida.core.model.Finding
import com.aegida.core.model.HomeState
import com.aegida.core.text.Texts
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * What the owner sees when she opens Aegida: one mark, one sentence, one big button. HomeState decides which;
 * this only draws it.
 */
@Composable
fun HomeScreen(
    home: HomeState,
    pictureId: String?,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
    onFix: (Finding) -> Unit,
    onCall: () -> Unit,
) {
    AegidaScreen(pictureId) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (home) {
                is HomeState.NoKnownProblems -> {
                    StatusMark(Mark.Good)
                    Headline(Texts.Home.ALL_GOOD)
                    Body(TimeText.checkedLine(home.checkedAt, now, zone, locale))
                    Body(Texts.Home.NO_KNOWN_PROBLEMS)
                    QuietButton(Texts.Home.CALL_A_GUARDIAN, onCall)
                }
                is HomeState.NeedsTaps -> {
                    val first = home.items.first()
                    StatusMark(Mark.NeedsTap)
                    Headline(if (home.items.size == 1) Texts.Home.NEEDS_ONE else Texts.Home.NEEDS_MANY.format(home.items.size))
                    Body(first.key.problem.forMom)
                    BigButton(Texts.Home.fixButton(first.key.problem.fix), { onFix(first) })
                    QuietButton(Texts.Home.CALL_A_GUARDIAN, onCall)
                }
                is HomeState.CouldntCheck -> {
                    StatusMark(Mark.Unknown)
                    if (home.lastCheckedAt == null) {
                        Headline(Texts.Home.NEVER_CHECKED)
                        Body(Texts.Home.NEVER_CHECKED_WHY)
                    } else {
                        Headline(Texts.Home.COULDNT_CHECK)
                        Body(Texts.Home.COULDNT_CHECK_WHY)
                        Body(TimeText.lastCheckedLine(home.lastCheckedAt!!, now, zone, locale))
                    }
                    QuietButton(Texts.Home.CALL_A_GUARDIAN, onCall)
                }
            }
        }
    }
}

@Composable
fun Headline(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = modifier.semantics { heading() })
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, centered: Boolean = true) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = if (centered) TextAlign.Center else TextAlign.Start, modifier = modifier)
}
