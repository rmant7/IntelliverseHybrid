package com.intelliverse.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.intelliverse.models.DownloadState
import com.intelliverse.models.LocalModelSeed
import com.intelliverse.models.ModelsViewModel

/**
 * Host-level catalog of on-device translation models: browse, download,
 * cancel, delete. This only manages the model FILES on disk -- no local
 * inference happens here yet, that needs a native (llama.cpp JNI) runtime
 * this app doesn't build yet. Reachable from the start screen's overflow
 * menu, same as Log/Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(navController: NavController, viewModel: ModelsViewModel = hiltViewModel()) {
    val states by viewModel.downloads.states.collectAsState()

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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(viewModel.catalog) { seed ->
                ModelCard(
                    seed = seed,
                    state = states[seed.id] ?: DownloadState.Idle,
                    onDownload = { viewModel.downloads.start(seed) },
                    onCancel = { viewModel.downloads.cancel(seed) },
                    onDelete = { viewModel.downloads.delete(seed) },
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    seed: LocalModelSeed,
    state: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(seed.title, style = MaterialTheme.typography.titleMedium)
            Text(
                seed.paramsLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (seed.note.isNotBlank()) {
                Text(
                    seed.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "~${formatBytes(seed.approxSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                when (state) {
                    is DownloadState.Idle -> Button(onClick = onDownload) { Text("Download") }
                    is DownloadState.Resolving -> OutlinedButton(onClick = {}, enabled = false) { Text("Resolving…") }
                    is DownloadState.Running -> {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                            val progress = if (state.totalBytes > 0) {
                                (state.downloadedBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(onClick = onCancel) { Text("Cancel") }
                            }
                        }
                    }
                    is DownloadState.Installed -> {
                        Text(
                            "Installed",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                    is DownloadState.Failed -> {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = onDownload) { Text("Retry") }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    else -> "$bytes B"
}
