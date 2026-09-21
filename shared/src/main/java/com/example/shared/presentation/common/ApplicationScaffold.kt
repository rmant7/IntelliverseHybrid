package com.example.shared.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ApplicationScaffold(
    modifier: Modifier = Modifier,
    isShowed: Boolean,
    columnModifier: Modifier = Modifier,
    columnVerticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    columnHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    // ColumnScope, not a plain lambda: lets a caller's content -- notably
    // the result screens' solution display -- use Modifier.weight(1f) to
    // claim whatever height its sibling rows (progress bar, AI-service
    // selector, action buttons, audio player) don't need, instead of a
    // fixed guessed fraction of the whole screen regardless of how much
    // those siblings actually take.
    content: @Composable ColumnScope.() -> Unit,
    bottomBar: @Composable () -> Unit = {},
    topBar: @Composable () -> Unit = {},
) {
    if (isShowed) {
        Scaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            content = { paddingValues ->
                Column(
                    modifier = columnModifier
                        .fillMaxSize()
                        .padding(paddingValues = paddingValues),
                        //.padding(16.dp),
                    verticalArrangement = columnVerticalArrangement,
                    horizontalAlignment = columnHorizontalAlignment
                ) {
                    content()
                }
            }
        )
    }
}