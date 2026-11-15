package com.aegida.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.aegida.core.alert.Reach
import com.aegida.core.model.Finding
import com.aegida.core.model.HomeState
import com.aegida.core.text.Fix
import com.aegida.core.text.GuardianPhoneWords
import com.aegida.core.text.Texts

/** Where "Call a guardian" is offered: the button, the number as words when there is no dialer, or "ask Sam" when there is no number. */
@Composable
private fun CallOrAsk(guardian: GuardianReach, askWhenNoNumber: Boolean) {
    when (guardian.reach) {
        Reach.Button -> QuietButton(Texts.Home.CALL_A_GUARDIAN, guardian.onCall)
        Reach.NumberOnly -> Body(GuardianPhoneWords.guardianNumber(guardian.phone))
        Reach.None -> if (askWhenNoNumber) Body(GuardianPhoneWords.unsureAsk(guardian.name))
    }
}

/**
 * The buttons pinned above the tab bar, or null for none. "Fix my phone" for an unreachable guardian only dials: with nothing to dial
 * there is no button at all (K10); "Call a guardian" only where a call can be made (T4).
 */
private fun pinnedButtons(first: Finding, guardian: GuardianReach, onFix: (Finding) -> Unit): (@Composable ColumnScope.() -> Unit)? {
    val canCall = guardian.reach == Reach.Button
    val big = first.key.problem.fix != Fix.CallGuardian || canCall
    return if (!big && !canCall) {
        null
    } else {
        {
            if (big) BigButton(Texts.Home.fixButton(first.key.problem.fix), { onFix(first) })
            if (canCall) QuietButton(Texts.Home.CALL_A_GUARDIAN, guardian.onCall)
        }
    }
}

/**
 * What the owner sees when she opens Aegida: one mark, one sentence, one big button. HomeState decides which;
 * this only draws it.
 */
@Composable
fun HomeScreen(
    home: HomeState,
    pictureId: String?,
    shown: ShownAt,
    onFix: (Finding) -> Unit,
    guardian: GuardianReach = GuardianReach(),
) {
    AegidaScreen(
        pictureId,
        // The main button and "Call a guardian" stay pinned above the tab bar; the words above them scroll (#171).
        pinned = (home as? HomeState.NeedsTaps)?.let { taps -> pinnedButtons(taps.items.first(), guardian, onFix) },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (home) {
                is HomeState.NoKnownProblems -> {
                    StatusMark(Mark.Good)
                    Headline(Texts.Home.ALL_GOOD)
                    Body(TimeText.checkedLine(home.checkedAt, shown.now, shown.zone, shown.locale))
                    Body(Texts.Home.NO_KNOWN_PROBLEMS)
                    CallOrAsk(guardian, askWhenNoNumber = true)
                }
                is HomeState.NeedsTaps -> {
                    val first = home.items.first()
                    StatusMark(Mark.NeedsTap)
                    Headline(if (home.items.size == 1) Texts.Home.NEEDS_ONE else Texts.Home.NEEDS_MANY.format(home.items.size))
                    Body(GuardianPhoneWords.forMom(first.key.problem, guardian.name, guardian.callable))
                    WhatItIsLines.of(first, shown).forEach { Body(it) }
                    if (first.key.problem.fix == Fix.CallGuardian && guardian.reach == Reach.NumberOnly) Body(GuardianPhoneWords.guardianNumber(guardian.phone))
                }
                is HomeState.CouldntCheck -> {
                    StatusMark(Mark.Unknown)
                    if (home.lastCheckedAt == null) {
                        Headline(Texts.Home.NEVER_CHECKED)
                        Body(Texts.Home.NEVER_CHECKED_WHY)
                        CallOrAsk(guardian, askWhenNoNumber = true)
                    } else {
                        Headline(Texts.Home.COULDNT_CHECK)
                        Body(GuardianPhoneWords.couldntCheckWhy(guardian.name, guardian.callable))
                        Body(TimeText.lastCheckedLine(home.lastCheckedAt!!, shown.now, shown.zone, shown.locale))
                        CallOrAsk(guardian, askWhenNoNumber = false)
                    }
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
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = if (centered) TextAlign.Center else TextAlign.Start, modifier = modifier.fillMaxWidth())
}
