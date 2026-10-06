package com.example.shared.presentation.screens.output.result

import ai.localstudio.sdk.LocalCapability
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shared.R
import com.intelliverse.localai.LocalModelAdvice
import com.intelliverse.localai.LocalModelNavigation
import com.intelliverse.localai.ModelOption
import java.util.Locale

/**
 * No on-device model can take this request now: which model to get -- one
 * tap opens it on the Models screen -- or how much memory to free when even
 * the smallest would not fit. Photos stay on the phone, so this is the way
 * to an answer for one.
 *
 * English only for now (see [R.string]'s own values/ entries): the strings
 * live in strings.xml precisely so a translation is just a values-xx/ file
 * away, the same as every other dialog in this app -- not hardcoded here.
 */
@Composable
fun LocalModelAdviceDialog(advice: LocalModelAdvice, onDismiss: () -> Unit) {
    fun open(option: ModelOption) {
        onDismiss()
        LocalModelNavigation.openModel(advice.capability, option.modelId)
    }
    val vision = advice.capability == LocalCapability.VISION
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (vision) R.string.local_advice_title_vision else R.string.local_advice_title_text)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vision) Text(stringResource(R.string.local_advice_vision_only_on_device))
                Text(advice.why, style = MaterialTheme.typography.bodySmall)
                if (advice.fitting.isNotEmpty()) {
                    Text(stringResource(R.string.local_advice_fitting_header, gb(advice.availableBytes)), fontWeight = FontWeight.SemiBold)
                    advice.fitting.forEach { option ->
                        Button(onClick = { open(option) }, modifier = Modifier.fillMaxWidth()) { Text(label(option)) }
                    }
                } else {
                    advice.smallest?.let { smallest ->
                        Text(
                            stringResource(
                                R.string.local_advice_smallest_needs,
                                smallest.title,
                                gb(smallest.needBytes),
                                gb(advice.availableBytes),
                                gb(advice.freeBytes ?: 0),
                            ),
                        )
                        OutlinedButton(onClick = { open(smallest) }, modifier = Modifier.fillMaxWidth()) { Text(label(smallest)) }
                    } ?: Text(stringResource(R.string.local_advice_none_in_catalog))
                }
                if (vision) {
                    Text(stringResource(R.string.local_advice_vision_part_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.local_advice_close)) } },
    )
}

@Composable
private fun label(option: ModelOption): String =
    stringResource(
        if (option.needsVisionPart) R.string.local_advice_add_vision_to else R.string.local_advice_download,
        option.title,
        gb(option.needBytes),
    )

private fun gb(bytes: Long): String = String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0)
