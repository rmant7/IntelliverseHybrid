package com.example.shared.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.shared.R.string
import com.example.shared.domain.language.Language
import com.example.shared.domain.language.LanguageRegistry
import java.util.Locale

/**
 * Replaces [ExposedDropBox] for the one field that genuinely can't be a
 * plain dropdown any more: 418 languages ([LanguageRegistry.ALL]) instead
 * of the old 49. Same outward field shape as ExposedDropBox (a clickable,
 * read-only OutlinedTextField) so it drops into the same layout slot, but
 * tapping it opens a full-screen search dialog instead of an anchored menu
 * -- an ExposedDropdownMenu with 418 rows is a scroll-to-find-it wall, not
 * a picker.
 *
 * No preemptive SortedIndexCache/lazy-per-locale sort: 418 entries is a
 * trivial amount of data for a LazyColumn and a String.contains filter --
 * only worth revisiting if profiling on a real device actually shows a
 * problem, not before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerField(
    modifier: Modifier = Modifier,
    label: Int,
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    recent: List<Language> = LanguageRegistry.INITIAL_QUICK_LIST,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    // Same visual shell as ExposedDropBox (OutlinedTextField inside an
    // ExposedDropdownMenuBox) purely so this field looks identical to every
    // other dropdown-style field on the same screen -- the box never
    // actually expands its own menu, expanded is always false here.
    ExposedDropdownMenuBox(
        expanded = false,
        onExpandedChange = { dialogOpen = true },
    ) {
        OutlinedTextField(
            modifier = modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .clickable { dialogOpen = true }
                .fillMaxWidth(),
            value = "${selectedLanguage.displayName(Locale.getDefault())} — ${selectedLanguage.nativeName()}",
            readOnly = true,
            textStyle = TextStyle(textAlign = TextAlign.Start),
            onValueChange = {},
            label = { Text(text = stringResource(id = label), textAlign = TextAlign.Start) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
        )
    }

    if (dialogOpen) {
        LanguagePickerDialog(
            title = stringResource(id = label),
            selectedLanguage = selectedLanguage,
            recent = recent,
            onLanguageSelected = {
                onLanguageSelected(it)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerDialog(
    title: String,
    selectedLanguage: Language,
    recent: List<Language>,
    onLanguageSelected: (Language) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiLocale = Locale.getDefault()
    var query by remember { mutableStateOf("") }

    // Computed once per dialog open (and again only if the UI locale
    // itself somehow changes mid-dialog, which it won't), not per
    // keystroke -- filtering then reuses this instead of re-running ICU
    // display-name lookups for all 418 entries on every character typed.
    val searchIndex = remember(uiLocale) {
        LanguageRegistry.ALL.map { language ->
            language to listOf(
                language.code,
                language.englishName,
                language.nativeName(),
                language.displayName(uiLocale),
            ).joinToString(" ").lowercase(Locale.ROOT)
        }
    }

    val results = remember(query, searchIndex) {
        if (query.isBlank()) {
            emptyList()
        } else {
            val needle = query.trim().lowercase(Locale.ROOT)
            searchIndex.filter { (_, haystack) -> haystack.contains(needle) }.map { it.first }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null)
                            }
                        },
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(string.search_language_placeholder)) },
                    )
                }
            },
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (query.isBlank()) {
                    item { SectionHeader(stringResource(string.recent_languages_header)) }
                    items(recent, key = { "recent-${it.code}" }) { language ->
                        LanguageRow(language, uiLocale, language == selectedLanguage, onLanguageSelected)
                    }
                    item { HorizontalDivider() }
                    item { SectionHeader(stringResource(string.all_languages_header)) }
                    items(LanguageRegistry.ALL, key = { "all-${it.code}" }) { language ->
                        LanguageRow(language, uiLocale, language == selectedLanguage, onLanguageSelected)
                    }
                } else if (results.isEmpty()) {
                    item {
                        Text(
                            stringResource(string.no_languages_found),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(results, key = { "search-${it.code}" }) { language ->
                        LanguageRow(language, uiLocale, language == selectedLanguage, onLanguageSelected)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    language: Language,
    uiLocale: Locale,
    isSelected: Boolean,
    onSelected: (Language) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onSelected(language) },
        headlineContent = { Text(language.displayName(uiLocale)) },
        supportingContent = { Text(language.nativeName()) },
        trailingContent = if (isSelected) {
            { Text("✓", color = MaterialTheme.colorScheme.primary) }
        } else null,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
