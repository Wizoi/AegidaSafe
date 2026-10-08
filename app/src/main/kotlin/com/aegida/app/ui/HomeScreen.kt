package com.aegida.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aegida.core.text.Texts

/** Placeholder until HomeState.of(findings, now) exists; screens only draw. */
@Composable
fun HomeScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(Texts.APP_NAME, style = MaterialTheme.typography.displayMedium)
            Text(Texts.HOME_NOT_SET_UP, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
