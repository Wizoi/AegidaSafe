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

/** The whole app: first-run setup until it is finished, then Home, History and My settings. */
@Composable
fun AppRoot(vm: MainViewModel = hiltViewModel()) {
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
        else -> MainContent(state, vm)
    }
}
