package com.intelliverse.presentation

import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.intelliverse.R
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
    /** Opened on one model (a mini-app's offer): the search shows just its card, ready to download. */
    initialModelId: String? = null,
) {
    val states by viewModel.downloads.states.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val running by viewModel.runningCheck.collectAsState()
    val context = LocalContext.current
    // Read so a finished check redraws what it changed.
    @Suppress("UNUSED_VARIABLE") val checksVersion = viewModel.checksVersion

    LaunchedEffect(initialPurpose) { initialPurpose?.let { viewModel.purpose = it } }
    LaunchedEffect(initialModelId) {
        initialModelId?.let(com.intelliverse.models.LocalModelCatalog::byId)?.let {
            viewModel.query = it.id
            viewModel.status = com.intelliverse.models.StatusFilter.ALL
        }
    }
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
                title = { Text(stringResource(R.string.models_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                        stringResource(R.string.models_native_unavailable),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item { InUseCard(viewModel, selection.chatModelId, selection.translationModelId) }
            item {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.query = it },
                    placeholder = { Text(stringResource(R.string.models_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (viewModel.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.query = "" }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear)) }
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
                        FilterChip(selected = viewModel.purpose == f, onClick = { viewModel.purpose = f }, label = { Text(f.label()) })
                    }
                    item { Spacer(Modifier.width(8.dp)) }
                    items(StatusFilter.entries.toList()) { f ->
                        FilterChip(selected = viewModel.status == f, onClick = { viewModel.status = f }, label = { Text(f.label()) })
                    }
                }
            }
            val visible = viewModel.visible(states)
            if (visible.isEmpty()) {
                item { Text(stringResource(R.string.models_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

/** [PurposeFilter]'s on-screen label (the filter itself carries no Context to resolve one). */
@Composable
private fun PurposeFilter.label(): String = when (this) {
    PurposeFilter.ALL -> stringResource(R.string.label_all_models)
    PurposeFilter.CHAT -> stringResource(R.string.label_chat)
    PurposeFilter.TRANSLATION -> stringResource(R.string.label_translation)
    PurposeFilter.VISION -> stringResource(R.string.label_images)
}

/** [StatusFilter]'s on-screen label. */
@Composable
private fun StatusFilter.label(): String = when (this) {
    StatusFilter.ALL -> stringResource(R.string.label_any)
    StatusFilter.INSTALLED -> stringResource(R.string.label_installed)
    StatusFilter.AVAILABLE -> stringResource(R.string.label_not_installed)
    StatusFilter.CHECKED -> stringResource(R.string.label_checked)
}

/** Which model answers chat and which translates, at a glance. */
@Composable
private fun InUseCard(viewModel: ModelsViewModel, chosenChat: String?, chosenTranslation: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.models_in_use), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            InUseLine(stringResource(R.string.label_chat), viewModel.chatModel(), chosen = chosenChat != null)
            InUseLine(stringResource(R.string.label_translation), viewModel.translationModel(), chosen = chosenTranslation != null)
        }
    }
}

@Composable
private fun InUseLine(what: String, model: LocalModelSeed?, chosen: Boolean) {
    Text(
        when {
            model == null -> stringResource(R.string.models_in_use_line_none, what)
            chosen -> stringResource(R.string.models_in_use_line_chosen, what, model.title)
            else -> stringResource(R.string.models_in_use_line_best, what, model.title)
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
                        "${seed.paramsLabel} · ~${ModelsViewModel.formatBytes(seed.approxSizeBytes)}" + when {
                            !seed.vision -> ""
                            seed.projectorApproxBytes > 0 -> " + vision ~${ModelsViewModel.formatBytes(seed.projectorApproxBytes)}"
                            else -> " + vision part"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (installed && ModelPurpose.CHAT in seed.purposes && !forChat) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_use_for_chat)) }, onClick = { menu = false; viewModel.useForChat(seed) })
                        }
                        if (installed && ModelPurpose.TRANSLATION in seed.purposes && !forTranslation) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_use_for_translation)) }, onClick = { menu = false; viewModel.useForTranslation(seed) })
                        }
                        if (installed) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_try_it)) }, onClick = { menu = false; viewModel.tryFor = seed })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_check_on_phone)) }, onClick = { menu = false; viewModel.verify(seed) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_details)) }, onClick = { menu = false; viewModel.detailsFor = seed })
                        if (installed || state is DownloadState.Failed || state is DownloadState.Paused) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; viewModel.delete(seed) })
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ModelPurpose.CHAT in seed.purposes) Tag(if (forChat) stringResource(R.string.tag_chat_selected) else stringResource(R.string.label_chat), strong = forChat)
                if (ModelPurpose.TRANSLATION in seed.purposes) {
                    Tag(if (forTranslation) stringResource(R.string.tag_translation_selected) else stringResource(R.string.label_translation), strong = forTranslation)
                }
                if (seed.vision) Tag(stringResource(R.string.label_images), strong = installed && viewModel.sees(seed))
            }
            if (seed.note.isNotBlank()) {
                Text(seed.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (installed) CheckLine(check, running)
            if (installed && seed.vision && !viewModel.sees(seed)) VisionMissing(seed, viewModel, onDownload)
            DownloadRow(seed, state, viewModel, onDownload, forChat, forTranslation)
        }
    }
}

/** A vision model whose weights are in but not its vision part: it chats, it cannot see yet. */
@Composable
private fun VisionMissing(seed: LocalModelSeed, viewModel: ModelsViewModel, onDownload: () -> Unit) {
    val errors by viewModel.projectorErrors.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            errors[seed.id]?.let { stringResource(R.string.models_vision_part_error, it) } ?: stringResource(R.string.models_vision_part_missing),
            style = MaterialTheme.typography.bodySmall,
            color = if (errors[seed.id] != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDownload) { Text(if (errors[seed.id] != null) stringResource(R.string.action_retry) else stringResource(R.string.action_add)) }
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
        running != null -> if (running.question == 0) stringResource(R.string.check_loading) else stringResource(R.string.check_question, running.question, running.questions)
        check == null || check.stored == null -> stringResource(R.string.check_not_yet)
        check.stored.error != null -> stringResource(R.string.check_failed, check.stored.error!!)
        else -> check.results.entries.joinToString(" · ") { (cap, result) ->
            capabilityLabel(cap.name) + " " + when (result) {
                CheckResult.PASS -> stringResource(R.string.check_pass_mark)
                CheckResult.FAIL -> stringResource(R.string.check_fail_mark)
                CheckResult.STALE -> stringResource(R.string.check_out_of_date)
                CheckResult.NOT_TESTED -> stringResource(R.string.check_not_tested)
            }
        } + (check.staleReason?.let { stringResource(R.string.check_stale_suffix, it) } ?: "")
    }
    val good = check?.results?.isNotEmpty() == true && check.results.values.all { it == CheckResult.PASS } && running == null
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** [ai.localstudio.sdk.LocalCapability]'s on-screen label, for the live check line above (the diagnostic report's own [ModelsViewModel.capabilityLabel] stays plain-English). */
@Composable
private fun capabilityLabel(name: String): String = when (name) {
    ai.localstudio.sdk.LocalCapability.TEXT.name -> stringResource(R.string.label_chat)
    ai.localstudio.sdk.LocalCapability.TRANSLATION.name -> stringResource(R.string.label_translation)
    ai.localstudio.sdk.LocalCapability.VISION.name -> stringResource(R.string.label_images)
    else -> name
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
        is DownloadState.Idle -> Button(onClick = onDownload) { Text(stringResource(R.string.action_download)) }
        is DownloadState.Resolving -> OutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.download_resolving)) }
        is DownloadState.Running -> Column(Modifier.fillMaxWidth()) {
            val progress = if (state.totalBytes > 0) (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (state.projector) stringResource(R.string.download_vision_prefix) else "") +
                        "${ModelsViewModel.formatBytes(state.downloadedBytes)} / ${ModelsViewModel.formatBytes(state.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.downloads.cancel(seed) }) { Text(stringResource(com.example.shared.R.string.cancel)) }
            }
        }
        is DownloadState.Paused -> Column(Modifier.fillMaxWidth()) {
            val progress = if (state.totalBytes > 0) (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (state.projector) stringResource(R.string.download_vision_paused_prefix) else stringResource(R.string.download_paused_prefix)) +
                        "${ModelsViewModel.formatBytes(state.downloadedBytes)} of ~${ModelsViewModel.formatBytes(state.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.delete(seed) }) { Text(stringResource(R.string.action_delete)) }
                Button(onClick = onDownload) { Text(stringResource(R.string.action_resume)) }
            }
        }
        is DownloadState.Installed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The one action that matters next: put it to use, for whatever it is not used for yet.
            when {
                ModelPurpose.CHAT in seed.purposes && !forChat -> Button(onClick = { viewModel.useForChat(seed) }) { Text(stringResource(R.string.action_use_for_chat)) }
                ModelPurpose.TRANSLATION in seed.purposes && !forTranslation -> Button(onClick = { viewModel.useForTranslation(seed) }) { Text(stringResource(R.string.action_use_for_translation)) }
                else -> OutlinedButton(onClick = { viewModel.tryFor = seed }) { Text(stringResource(R.string.action_try_it)) }
            }
        }
        is DownloadState.Failed -> Column {
            Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onDownload) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

/** Everything about one model and its check, selectable and copyable in one tap. */
@Composable
private fun DetailsSheet(seed: LocalModelSeed, viewModel: ModelsViewModel) {
    val context = LocalContext.current
    val report = viewModel.report(seed)
    val copiedLabel = stringResource(R.string.toast_copied)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
        Text(seed.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.padding(4.dp))
        SelectionContainer { Text(report, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(seed.title, report))
                Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.action_copy)) }
            if (viewModel.isInstalled(seed)) {
                OutlinedButton(onClick = { viewModel.verify(seed) }) { Text(stringResource(R.string.action_check_on_phone)) }
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
    val context = LocalContext.current
    var picture by remember { mutableStateOf<ai.localstudio.sdk.LocalImage?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picture = com.intelliverse.localai.LocalImages.fromUri(context, uri)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.try_title, seed.title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.try_text_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = target,
            onValueChange = { target = it.trim() },
            label = { Text(stringResource(R.string.try_target_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.tryTranslate(seed, target, text) }, enabled = !viewModel.tryBusy && text.isNotBlank() && target.isNotBlank()) {
                Text(stringResource(R.string.action_translate))
            }
            if (canChat) {
                OutlinedButton(onClick = { viewModel.tryChat(seed, text, picture) }, enabled = !viewModel.tryBusy && text.isNotBlank()) {
                    Text(if (picture != null) stringResource(R.string.action_ask_about_picture) else stringResource(R.string.action_ask))
                }
            }
        }
        if (canChat && viewModel.sees(seed)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (picture != null) stringResource(R.string.models_picture_attached) else stringResource(R.string.models_try_can_see_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (picture != null) TextButton(onClick = { picture = null }) { Text(stringResource(R.string.action_remove)) }
                TextButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.action_pick)) }
            }
        }
        if (viewModel.tryBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (viewModel.tryOutput.isNotBlank()) SelectionContainer { Text(viewModel.tryOutput) }
    }
}
