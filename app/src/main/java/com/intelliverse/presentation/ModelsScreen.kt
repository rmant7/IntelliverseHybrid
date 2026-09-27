package com.intelliverse.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * Host-level Models management -- the future home for downloading/managing
 * on-device translation models (TranslateGemma, OmniTranslate, ...) shared
 * across mini-apps, rather than each mini-app owning its own copy. Reachable
 * from the start screen's overflow menu, same as Log/Settings. Empty shell
 * for now; the actual local-model runtime/download stack is a separate,
 * much larger piece of work not yet ported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Models") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                text = "No local models yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
