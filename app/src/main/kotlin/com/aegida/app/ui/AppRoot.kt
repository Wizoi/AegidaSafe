package com.aegida.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aegida.app.MainViewModel
import com.aegida.core.model.FindingKey
import com.aegida.core.model.HomeState
import com.aegida.core.text.Fix

/** The whole app: first-run setup until it is finished, then Home, History and My settings. [asked]: a tapped banner's warning (#164). */
@Composable
fun AppRoot(vm: MainViewModel = hiltViewModel(), asked: FindingKey? = null, onAsked: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    // Coming back from a Settings screen: look at the phone again so what she sees is current.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    when {
        !state.loaded -> AegidaScreen(null) {}
        !state.setup.done -> Box(Modifier.navigationBarsPadding()) { SetupFlow(state, vm) }
        else -> MainContent(state, vm, asked, onAsked)
    }
}

/** The page a tapped banner opens: the question for that warning while it still needs her, else none (Home). */
internal fun questionPageFor(asked: FindingKey, home: HomeState): Page {
    val open = (home as? HomeState.NeedsTaps)?.items?.firstOrNull { it.key == asked } ?: return Page.None
    val fix = open.key.problem.fix
    return if (fix == Fix.ConfirmChange || fix == Fix.ConfirmEvent) Page.Confirm(open) else Page.None
}
