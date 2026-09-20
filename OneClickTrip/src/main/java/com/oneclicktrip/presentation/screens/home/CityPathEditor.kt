package com.oneclicktrip.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneclicktrip.R

@Composable
fun CityPathEditor(
    selectedCities: List<String>,
    onAddCity: (String) -> Unit,
    onRemoveCity: (String) -> Unit,
    onMoveCity: (fromIndex: Int, toIndex: Int) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    val enterLocationLabel = stringResource(R.string.enter_location)

    Column {
        Text(stringResource(R.string.travel_path), style = MaterialTheme.typography.titleMedium)

        // Input field for new city
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(enterLocationLabel) },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onAddCity(text.trim())
                        text = ""
                    }
                }
            ) {
                Text(stringResource(R.string.add))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Display selected cities in order
        selectedCities.forEachIndexed { index, city ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Show position and arrow
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )

                Text(
                    text = city,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                // Arrow up
                if (index > 0) {
                    IconButton(onClick = { onMoveCity(index, index - 1) }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                    }
                }

                // Arrow down
                if (index + 1 < selectedCities.size) {
                    IconButton(onClick = { onMoveCity(index, index + 1) }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                    }
                }

                IconButton(onClick = { onRemoveCity(city) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }

            // Optional: show a visual arrow between rows
            if (index + 1 < selectedCities.size) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp), // align with city list
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("↓")
                }
            }
        }
    }
}