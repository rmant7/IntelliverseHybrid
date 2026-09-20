package com.example.shared.presentation

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.shared.R.string

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExposedDropBox(
    modifier: Modifier = Modifier,
    dropMenuModifier: Modifier = Modifier,
    maxHeightIn: Dp? = null,
    label: Int,
    selectedOption: T?,
    options: List<T>,
    onOptionSelected: (T?) -> Unit,
    optionToString: (T, Context) -> String,
    valueRequired: Boolean = false
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val notRelevant = stringResource(string.not_relevant)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = !expanded
        }
    ) {
        OutlinedTextField(
            modifier = modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .clickable { expanded = !expanded }
                .fillMaxWidth(),
            value = selectedOption?.let { optionToString(it, context) } ?: notRelevant,
            readOnly = true,
            textStyle = TextStyle(textAlign = TextAlign.Start),
            onValueChange = { },
            label = {
                Text(
                    text = stringResource(id = label),
                    textAlign = TextAlign.Start
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                )
            }
        )
        ExposedDropdownMenu(
            modifier = dropMenuModifier
                .heightIn(max = maxHeightIn ?: Dp.Infinity),
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (!valueRequired) {
                DropdownMenuItem(
                    onClick = {
                        onOptionSelected(null)
                        expanded = false
                    },
                    text = {
                        Text(
                            text = stringResource(string.not_relevant),
                            textAlign = TextAlign.Start
                        )
                    }
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    text = {
                        Text(
                            text = optionToString(option, context),
                            textAlign = TextAlign.Start
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MultiSelectExposedDropBox(
    modifier: Modifier = Modifier,
    dropMenuModifier: Modifier = Modifier,
    maxHeightIn: Dp? = null,
    label: Int,
    selectedOptions: List<T>,
    options: List<T>,
    onOptionsChanged: (List<T>) -> Unit,
    optionToString: (T, Context) -> String
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val summary = if (selectedOptions.isEmpty()) {
        stringResource(id = string.not_relevant)
    } else {
        selectedOptions.joinToString { optionToString(it, context) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .clickable { expanded = !expanded }
                .fillMaxWidth(),
            value = summary,
            readOnly = true,
            textStyle = TextStyle(textAlign = TextAlign.Start),
            onValueChange = {},
            label = {
                Text(
                    text = stringResource(id = label),
                    textAlign = TextAlign.Start
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        )

        ExposedDropdownMenu(
            modifier = dropMenuModifier.heightIn(max = maxHeightIn ?: Dp.Infinity),
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                val isSelected = option in selectedOptions
                DropdownMenuItem(
                    onClick = {
                        val updatedList = if (isSelected) {
                            selectedOptions - option
                        } else {
                            selectedOptions + option
                        }
                        onOptionsChanged(updatedList)
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(optionToString(option, context))
                        }
                    }
                )
            }
        }
    }
}