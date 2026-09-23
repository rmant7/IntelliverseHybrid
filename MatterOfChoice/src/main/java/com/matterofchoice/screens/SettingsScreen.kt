package com.matterofchoice.screens

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.matterofchoice.MainActivity
import com.matterofchoice.R
import com.matterofchoice.utils.LanguagePreferenceHelper
import com.matterofchoice.utils.LocaleHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val activity = LocalContext.current as? Activity // For recreating

    val languageDisplayNames = stringArrayResource(id = R.array.languages)
    val languageCodes = stringArrayResource(id = R.array.language_codes) // Ensure this exists

    val currentSelectedLanguageCode = remember {
        mutableStateOf(LanguagePreferenceHelper.getSelectedLanguage(context))
    }

    val currentLanguageDisplayName = remember(currentSelectedLanguageCode.value) {
        val index = languageCodes.indexOf(currentSelectedLanguageCode.value)
        if (index != -1 && index < languageDisplayNames.size) {
            languageDisplayNames[index]
        } else {
            "Select Language" // Or get system default display name
        }
    }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.settings_hint_select_language), // "Select your language"
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = currentLanguageDisplayName,
                onValueChange = {}, // Not editable directly
                readOnly = true,
                label = { Text(stringResource(id = R.string.settings_hint_select_language)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor() // Important for proper positioning
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // Option to revert to System Default
                DropdownMenuItem(
                    text = { Text("System Default") }, // Consider localizing this too
                    onClick = {
                        LocaleHelper.setLocale(context, null) // Pass null to revert
                        currentSelectedLanguageCode.value = ""
                        expanded = false
                        Log.d("SettingsScreen", "Activity instance: $activity")
                        activity?.recreate() // Recreate the activity to apply changes
                    }
                )

                languageDisplayNames.forEachIndexed { index, displayName ->
                    val languageCode = languageCodes.getOrNull(index)
                    if (languageCode != null) {
                        DropdownMenuItem(
                            text = { Text(displayName) },
                            onClick = {
                                if (currentSelectedLanguageCode.value != languageCode) {

                                    LocaleHelper.setLocale(context.applicationContext, languageCode) // Use applicationContext for consistency
                                    currentSelectedLanguageCode.value = languageCode

                                    (context as? MainActivity)?.recreate()
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick ={ activity?.recreate() }) {
            Text("Save Settings") // Or just rely on immediate application
        }
    }
}
