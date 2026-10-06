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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intelliverse.localai.LocalModelAdvice
import com.intelliverse.localai.LocalModelNavigation
import com.intelliverse.localai.ModelOption
import java.util.Locale

/**
 * No on-device model can take this request now: which model to get -- one
 * tap opens it on the Models screen -- or how much memory to free when even
 * the smallest would not fit. Photos stay on the phone, so this is the way
 * to an answer for one.
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
        title = { Text(if (vision) "A model that can see is needed" else "An on-device model is needed") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vision) Text("Photos are answered on this phone only -- for now they are not sent to cloud models.")
                Text(advice.why, style = MaterialTheme.typography.bodySmall)
                if (advice.fitting.isNotEmpty()) {
                    Text("These fit in the ~${gb(advice.availableBytes)} free now:", fontWeight = FontWeight.SemiBold)
                    advice.fitting.forEach { option ->
                        Button(onClick = { open(option) }, modifier = Modifier.fillMaxWidth()) { Text(label(option)) }
                    }
                } else {
                    advice.smallest?.let { smallest ->
                        Text(
                            "Even the smallest, ${smallest.title}, needs ~${gb(smallest.needBytes)}; ~${gb(advice.availableBytes)} is free now. " +
                                "Close other apps (Local AI Studio, if a model is loaded there) to free ~${gb(advice.freeBytes ?: 0)}, then try again -- or get it now:",
                        )
                        OutlinedButton(onClick = { open(smallest) }, modifier = Modifier.fillMaxWidth()) { Text(label(smallest)) }
                    } ?: Text("No model in the catalog offers this.")
                }
                if (vision) {
                    Text(
                        "A model's vision part downloads right after the model itself. If its card then says " +
                            "\"Cannot see pictures yet\", tap Add on that card to download the vision part.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun label(option: ModelOption): String =
    (if (option.needsVisionPart) "Add vision to " else "Download ") + "${option.title} (needs ~${gb(option.needBytes)})"

private fun gb(bytes: Long): String = String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0)
