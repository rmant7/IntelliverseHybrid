package com.intelliverse.presentation

import ai.localstudio.sdk.CheckResult
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.intelliverse.localai.RunningCheck
import com.intelliverse.models.CheckView
import com.intelliverse.models.DownloadState
import com.intelliverse.models.LocalModelSeed
import com.intelliverse.models.ModelPurpose
import com.intelliverse.models.ModelsViewModel
import com.intelliverse.models.PurposeFilter
import com.intelliverse.models.StatusFilter

/**
 * Every on-device model in one place: search and filter by purpose and
 * status; at the top, which model answers chat and which translates; per
 * model its state, its check on this phone, and its actions. Tap a card for
 * the full check report (copyable). Reachable from the start screen's menu
 * and from Chat (to pick its model).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    navController: NavController,
    viewModel: ModelsViewModel = hiltViewModel(),
    initialPurpose: PurposeFilter? = null,
) {
    val states by viewModel.downloads.states.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val running by viewModel.runningCheck.collectAsState()
    val context = LocalContext.current
    // Read so a finished check redraws what it changed.
    @Suppress("UNUSED_VARIABLE") val checksVersion = viewModel.checksVersion

    LaunchedEffect(initialPurpose) { initialPurpose?.let { viewModel.purpose = it } }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.message = null
        }
    }

    // POST_NOTIFICATIONS (API 33+) is what lets the download's foreground notification show; asked when a download starts.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!viewModel.nativeAvailable) {
                item {
                    Text(
                        "On-device models can't run on this phone (unsupported processor).",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item { InUseCard(viewModel, selection.chatModelId, selection.translationModelId) }
            item {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.query = it },
                    placeholder = { Text("Search models") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (viewModel.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PurposeFilter.entries.toList()) { f ->
                        FilterChip(selected = viewModel.purpose == f, onClick = { viewModel.purpose = f }, label = { Text(f.label) })
                    }
                    item { Spacer(Modifier.width(8.dp)) }
                    items(StatusFilter.entries.toList()) { f ->
                        FilterChip(selected = viewModel.status == f, onClick = { viewModel.status = f }, label = { Text(f.label) })
                    }
                }
            }
            val visible = viewModel.visible(states)
            if (visible.isEmpty()) {
                item { Text("No model matches.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(visible, key = { it.id }) { seed ->
                ModelCard(
                    seed = seed,
                    state = states[seed.id] ?: DownloadState.Idle,
                    check = viewModel.check(seed),
                    running = running?.takeIf { it.modelId == seed.id },
                    forChat = viewModel.chatModel()?.id == seed.id,
                    forTranslation = viewModel.translationModel()?.id == seed.id,
                    viewModel = viewModel,
                    onDownload = { ensureNotificationPermission(); viewModel.downloads.start(seed) },
                )
            }
        }
    }

    viewModel.detailsFor?.let { seed ->
        ModalBottomSheet(onDismissRequest = { viewModel.detailsFor = null }) {
            DetailsSheet(seed, viewModel)
        }
    }
    viewModel.tryFor?.let { seed ->
        ModalBottomSheet(onDismissRequest = { viewModel.tryFor = null }) {
            TrySheet(seed, viewModel)
        }
    }
}

/** Which model answers chat and which translates, at a glance. */
@Composable
private fun InUseCard(viewModel: ModelsViewModel, chosenChat: String?, chosenTranslation: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("In use", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            InUseLine("Chat", viewModel.chatModel(), chosen = chosenChat != null)
            InUseLine("Translation", viewModel.translationModel(), chosen = chosenTranslation != null)
        }
    }
}

@Composable
private fun InUseLine(what: String, model: LocalModelSeed?, chosen: Boolean) {
    Text(
        "$what: " + when {
            model == null -> "none installed — download one below"
            chosen -> model.title
            else -> "${model.title} (best installed)"
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ModelCard(
    seed: LocalModelSeed,
    state: DownloadState,
    check: CheckView?,
    running: RunningCheck?,
    forChat: Boolean,
    forTranslation: Boolean,
    viewModel: ModelsViewModel,
    onDownload: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val installed = state is DownloadState.Installed
    Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.detailsFor = seed }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(seed.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${seed.paramsLabel} · ~${ModelsViewModel.formatBytes(seed.approxSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (installed && ModelPurpose.CHAT in seed.purposes && !forChat) {
                            DropdownMenuItem(text = { Text("Use for chat") }, onClick = { menu = false; viewModel.useForChat(seed) })
                        }
                        if (installed && ModelPurpose.TRANSLATION in seed.purposes && !forTranslation) {
                            DropdownMenuItem(text = { Text("Use for translation") }, onClick = { menu = false; viewModel.useForTranslation(seed) })
                        }
                        if (installed) {
                            DropdownMenuItem(text = { Text("Try it") }, onClick = { menu = false; viewModel.tryFor = seed })
                            DropdownMenuItem(text = { Text("Check on this phone") }, onClick = { menu = false; viewModel.verify(seed) })
                        }
                        DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; viewModel.detailsFor = seed })
                        if (installed || state is DownloadState.Failed) {
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; viewModel.delete(seed) })
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ModelPurpose.CHAT in seed.purposes) Tag(if (forChat) "Chat ✓" else "Chat", strong = forChat)
                if (ModelPurpose.TRANSLATION in seed.purposes) Tag(if (forTranslation) "Translation ✓" else "Translation", strong = forTranslation)
            }
            if (seed.note.isNotBlank()) {
                Text(seed.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (installed) CheckLine(check, running)
            DownloadRow(seed, state, viewModel, onDownload, forChat, forTranslation)
        }
    }
}

@Composable
private fun Tag(text: String, strong: Boolean) {
    SuggestionChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal) },
    )
}

/** The check in one line: per capability, out of date and why, or the question it is on. */
@Composable
private fun CheckLine(check: CheckView?, running: RunningCheck?) {
    val text = when {
        running != null -> if (running.question == 0) "Checking: loading…" else "Checking: question ${running.question} of ${running.questions}…"
        check == null || check.stored == null -> "Not checked on this phone yet"
        check.stored.error != null -> "Check failed: ${check.stored.error}"
        else -> check.results.entries.joinToString(" · ") { (cap, result) ->
            ModelsViewModel.capabilityLabel(cap.name) + " " + when (result) {
                CheckResult.PASS -> "✓"
                CheckResult.FAIL -> "✗"
                CheckResult.STALE -> "out of date"
                CheckResult.NOT_TESTED -> "not tested"
            }
        } + (check.staleReason?.let { "\n$it — check again" } ?: "")
    }
    val good = check?.results?.isNotEmpty() == true && check.results.values.all { it == CheckResult.PASS } && running == null
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DownloadRow(
    seed: LocalModelSeed,
    state: DownloadState,
    viewModel: ModelsViewModel,
    onDownload: () -> Unit,
    forChat: Boolean,
    forTranslation: Boolean,
) {
    when (state) {
        is DownloadState.Idle -> Button(onClick = onDownload) { Text("Download") }
        is DownloadState.Resolving -> OutlinedButton(onClick = {}, enabled = false) { Text("Looking up the file…") }
        is DownloadState.Running -> Column(Modifier.fillMaxWidth()) {
            val progress = if (state.totalBytes > 0) (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ModelsViewModel.formatBytes(state.downloadedBytes)} / ${ModelsViewModel.formatBytes(state.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.downloads.cancel(seed) }) { Text("Cancel") }
            }
        }
        is DownloadState.Installed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The one action that matters next: put it to use, for whatever it is not used for yet.
            when {
                ModelPurpose.CHAT in seed.purposes && !forChat -> Button(onClick = { viewModel.useForChat(seed) }) { Text("Use for chat") }
                ModelPurpose.TRANSLATION in seed.purposes && !forTranslation -> Button(onClick = { viewModel.useForTranslation(seed) }) { Text("Use for translation") }
                else -> OutlinedButton(onClick = { viewModel.tryFor = seed }) { Text("Try it") }
            }
        }
        is DownloadState.Failed -> Column {
            Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onDownload) { Text("Retry") }
        }
    }
}

/** Everything about one model and its check, selectable and copyable in one tap. */
@Composable
private fun DetailsSheet(seed: LocalModelSeed, viewModel: ModelsViewModel) {
    val context = LocalContext.current
    val report = viewModel.report(seed)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
        Text(seed.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.padding(4.dp))
        SelectionContainer { Text(report, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(seed.title, report))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
            if (viewModel.isInstalled(seed)) {
                OutlinedButton(onClick = { viewModel.verify(seed) }) { Text("Check on this phone") }
            }
        }
    }
}

/** A quick try: translate a sentence, or (for a chat model) ask a question. */
@Composable
private fun TrySheet(seed: LocalModelSeed, viewModel: ModelsViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("ru") }
    val canChat = ModelPurpose.CHAT in seed.purposes && !seed.isT5EncoderDecoder
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Try ${seed.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = target,
            onValueChange = { target = it.trim() },
            label = { Text("Translate into (language code, e.g. ru, he, fr)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.tryTranslate(seed, target, text) }, enabled = !viewModel.tryBusy && text.isNotBlank() && target.isNotBlank()) {
                Text("Translate")
            }
            if (canChat) {
                OutlinedButton(onClick = { viewModel.tryChat(seed, text) }, enabled = !viewModel.tryBusy && text.isNotBlank()) { Text("Ask") }
            }
        }
        if (viewModel.tryBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (viewModel.tryOutput.isNotBlank()) SelectionContainer { Text(viewModel.tryOutput) }
    }
}
