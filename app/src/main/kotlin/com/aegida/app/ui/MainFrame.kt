package com.aegida.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign

/** At very large text "My settings" wraps; every label gets two lines from this scale up, so the icons stay level. */
private const val TWO_LINE_LABEL_SCALE = 1.4f

/** The tab bar and the room it needs: whatever [content] draws sits above the bar, never under it. */
@Composable
internal fun MainFrame(tab: Tab, onTab: Boolean, onSelect: (Tab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // AegidaScreen adds the status bar space; the bar handles its own
        bottomBar = { TabBar(tab, onTab, onSelect) },
    ) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
private fun TabBar(tab: Tab, onTab: Boolean, onSelect: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { entry ->
            NavigationBarItem(
                selected = tab == entry && onTab,
                onClick = { onSelect(entry) },
                icon = { TabIcon(entry) },
                label = {
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        minLines = if (LocalDensity.current.fontScale > TWO_LINE_LABEL_SCALE) 2 else 1,
                    )
                },
            )
        }
    }
}
