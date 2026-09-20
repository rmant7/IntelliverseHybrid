package com.matterofchoice.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropDownMenu(
    itemsList: List<String>,
    isExposed: MutableState<Boolean>,
    selectedItem: MutableState<String>,
    hint: String,
) {
    ExposedDropdownMenuBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        expanded = isExposed.value,
        onExpandedChange = { isExposed.value = !isExposed.value }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            label = { if (selectedItem.value.isNotEmpty())Text(text = hint) },
            value = selectedItem.value.ifEmpty { hint },
            onValueChange = {},
            readOnly = true,
            placeholder = {
                if (selectedItem.value.isEmpty()) Text(text = hint)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExposed.value) }
        )
        ExposedDropdownMenu(
            modifier = Modifier.fillMaxWidth(),
            expanded = isExposed.value,
            onDismissRequest = { isExposed.value = false }
        ) {
            itemsList.forEachIndexed { index, text ->
                if(text.isNotEmpty()){
                    DropdownMenuItem(
                        text = { Text(text = text) },
                        onClick = {
                            selectedItem.value = itemsList[index]
                            isExposed.value = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }

            }
        }
    }
}