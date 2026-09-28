package com.intelliverse.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.intelliverse.models.DownloadState
import com.intelliverse.models.LocalModelSeed
import com.intelliverse.models.ModelsViewModel

/**
 * Host-level catalog of on-device translation models: browse, download,
 * cancel, delete, and (once installed) run a real local translation through
 * the native llama.cpp bridge. Reachable from the start screen's overflow
 * menu, same as Log/Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(navController: NavController, viewModel: ModelsViewModel = hiltViewModel()) {
    val states by viewModel.downloads.states.collectAsState()

    // POST_NOTIFICATIONS is declared in the manifest but, on API 33+, must
    // also be requested at runtime -- otherwise ModelDownloadService's
    // foreground-service notification silently never appears (the download
    // itself still runs either way; only the visible progress notification
    // is affected). Requested right before a download starts rather than
    // eagerly on screen entry, so it's tied to the action that actually
    // needs it.
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* download proceeds regardless -- see comment above */ }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!viewModel.nativeAvailable) {
                Text(
                    "Local inference isn't available on this build/device (unsupported ABI).",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(viewModel.catalog) { seed ->
                    ModelCard(
                        seed = seed,
                        state = states[seed.id] ?: DownloadState.Idle,
                        viewModel = viewModel,
                        onDownload = { ensureNotificationPermission(); viewModel.downloads.start(seed) },
                        onCancel = { viewModel.downloads.cancel(seed) },
                        onDelete = { viewModel.downloads.delete(seed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    seed: LocalModelSeed,
    state: DownloadState,
    viewModel: ModelsViewModel,
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
                        TextButton(onClick = { viewModel.toggleTest(seed) }) {
                            Text(if (viewModel.testExpandedId == seed.id) "Hide test" else "Test")
                        }
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

            if (state is DownloadState.Installed && viewModel.testExpandedId == seed.id) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                TestTranslatePanel(seed = seed, viewModel = viewModel)
            }
        }
    }
}

/**
 * A raw language-code text field rather than a full language picker: this is
 * a quick way to prove the native pipeline actually works end to end (load a
 * downloaded GGUF, run inference, stream text back), not the final
 * translation UI -- that would need its own language list and, per model, a
 * mapping to whatever code format that specific model expects (MADLAD wants
 * a bare target-language code like "ru"; OmniTranslate's own model card
 * recommends an ISO-639-3 + script code like "rus_Cyrl").
 */
@Composable
private fun TestTranslatePanel(seed: LocalModelSeed, viewModel: ModelsViewModel) {
    var targetLang by rememberSaveable { mutableStateOf("ru") }
    var inputText by rememberSaveable { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = targetLang,
            onValueChange = { targetLang = it },
            label = { Text("Target language code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Text to translate") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Button(
            onClick = { viewModel.translate(seed, targetLang, inputText) },
            enabled = !viewModel.isBusy && inputText.isNotBlank() && targetLang.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(if (viewModel.isBusy) "Translating…" else "Translate")
        }
        viewModel.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        if (viewModel.translateOutput.isNotBlank()) {
            Text(
                viewModel.translateOutput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    else -> "$bytes B"
}
