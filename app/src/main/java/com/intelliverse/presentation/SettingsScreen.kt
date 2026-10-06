package com.intelliverse.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.intelliverse.R

/**
 * Host-level Settings -- app-wide preferences, distinct from any mini-app's
 * own in-flow options (SchoolKiller/DietTracker/StyleTranslator/OneClickTrip
 * have none of their own; MatterOfChoice's own Settings screen is local to
 * its game setup, not this). Reachable from the start screen's overflow
 * menu (and, now that StyleTranslator actually uses local models, from
 * every mini-app's own top bar too).
 *
 * Storage management is the first real content here: with local translation
 * models now downloadable and actually used, "how much space is this taking
 * up, and how do I clear it" is a genuine host-level concern -- the Models
 * screen itself only offers deleting one model at a time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            val selection by viewModel.selection.collectAsState()
            Text(stringResource(R.string.settings_on_device_ai), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_chat_line, viewModel.chatModelTitle() ?: stringResource(R.string.settings_no_chat_model)) + "\n" +
                    stringResource(R.string.settings_translation_line, viewModel.translationModelTitle() ?: stringResource(R.string.settings_no_translation_model)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = { navController.navigate("models") }) { Text(stringResource(R.string.action_choose_models)) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_answer_in_mini_apps))
                    Text(
                        stringResource(R.string.settings_answer_in_mini_apps_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = selection.useInApps, onCheckedChange = { viewModel.setUseInApps(it) })
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.settings_local_models), style = MaterialTheme.typography.titleMedium)

            if (viewModel.installedModels.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_local_models),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    viewModel.installedModels.forEach { model ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(model.title)
                            Text(
                                formatBytes(model.sizeBytes),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.settings_total), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatBytes(viewModel.totalBytes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.action_delete_all_local_models))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.settings_delete_all_title)) },
            text = { Text(stringResource(R.string.settings_delete_all_text, formatBytes(viewModel.totalBytes))) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteAllModels()
                    showDeleteConfirm = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(com.example.shared.R.string.cancel)) }
            }
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    else -> "$bytes B"
}
