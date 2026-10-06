package com.intelliverse.presentation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.intelliverse.R
import com.intelliverse.localai.LocalImages
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

/** On-device chat with the model chosen on the Models screen (its title is in the bar; the gear opens Models on Chat). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, viewModel: ChatViewModel = hiltViewModel()) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val pictureUnreadable = stringResource(R.string.toast_picture_unreadable)
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.image = LocalImages.fromUri(context, uri)
            if (viewModel.image == null) Toast.makeText(context, pictureUnreadable, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(viewModel.turns.size, viewModel.turns.lastOrNull()?.text?.length) {
        if (viewModel.turns.isNotEmpty()) listState.animateScrollToItem(viewModel.turns.lastIndex)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.chat_title))
                        Text(
                            viewModel.modelTitle()?.let { stringResource(R.string.chat_subtitle_on_device, it) } ?: stringResource(R.string.chat_subtitle_no_model),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
                actions = {
                    IconButton(onClick = { viewModel.clear() }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_clear_conversation)) }
                    IconButton(onClick = { navController.navigate("models?purpose=CHAT") }) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_chat_model)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (viewModel.turns.isEmpty()) {
                    item {
                        Text(
                            if (viewModel.modelTitle() == null) stringResource(R.string.chat_empty_no_model) else stringResource(R.string.chat_empty_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                itemsIndexed(viewModel.turns) { _, turn -> Bubble(turn) }
            }
            if (viewModel.image != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        viewModel.modelTitle()?.let { stringResource(R.string.chat_picture_will_see, it) } ?: stringResource(R.string.chat_picture_none_sees),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (viewModel.canSee()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.image = null }) { Text(stringResource(R.string.action_remove)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !viewModel.busy,
                ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_attach_picture)) }
                OutlinedTextField(
                    value = viewModel.input,
                    onValueChange = { viewModel.input = it },
                    placeholder = { Text(stringResource(R.string.chat_message_hint)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                )
                if (viewModel.busy) {
                    IconButton(onClick = { viewModel.stop() }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_stop)) }
                } else {
                    IconButton(onClick = { viewModel.send() }, enabled = viewModel.input.isNotBlank()) {
                        Icon(Icons.Default.Send, contentDescription = stringResource(R.string.cd_send))
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(turn: ChatTurn) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (turn.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        turn.fromUser -> MaterialTheme.colorScheme.primaryContainer
                        turn.failed -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (turn.fromUser && turn.withImage) {
                Column {
                    Text(stringResource(R.string.chat_picture_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(turn.text) }
                }
            } else if (turn.note != null) {
                Column {
                    Text(stringResource(R.string.chat_answered_by, turn.note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (turn.thinking && turn.text.isEmpty()) {
                        Text(stringResource(R.string.chat_thinking), fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        SelectionContainer { Text(turn.text) }
                    }
                }
            } else if (turn.thinking && turn.text.isEmpty()) {
                Text(stringResource(R.string.chat_thinking), fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SelectionContainer { Text(turn.text) }
            }
        }
    }
}
