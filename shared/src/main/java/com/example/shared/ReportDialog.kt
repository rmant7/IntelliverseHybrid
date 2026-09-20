package com.example.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val reportContentLabel = stringResource(R.string.report_content)
    val selectReasonLabel = stringResource(R.string.select_reason)
    val offensiveLabel = stringResource(R.string.offensive)
    val hateSpeechLabel = stringResource(R.string.hate_speech)
    val incorrectInfoLabel = stringResource(R.string.incorrect_info)
    val otherLabel = stringResource(R.string.other)
    val submitLabel = stringResource(R.string.submit)
    val cancelLabel = stringResource(R.string.cancel)

    var selectedReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(reportContentLabel) },
        text = {
            Column {
                Text(selectReasonLabel)
                val reasons = listOf(offensiveLabel, hateSpeechLabel, incorrectInfoLabel, otherLabel)
                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp), // Add padding for better spacing
                        verticalAlignment = Alignment.CenterVertically // Align items properly
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Text(reason, modifier = Modifier.padding(start = 8.dp)) // Add space between radio and text
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSubmit(selectedReason)
                onDismiss()
            }) {
                Text(submitLabel)
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(cancelLabel)
            }
        }
    )
}