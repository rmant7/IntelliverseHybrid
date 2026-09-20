package com.intelliverse.presentation

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.example.shared.log.AppLog
import com.intelliverse.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val appLog: AppLog,
) : ViewModel() {
    var logText by mutableStateOf("")
        private set

    fun refresh() {
        logText = appLog.readAll()
    }

    fun clear() {
        appLog.clear()
        refresh()
    }
}

/**
 * Read-only view of [AppLog] -- the whole point is getting a report of what
 * went wrong OFF the phone: one tap copies everything (log plus build and
 * device info) to the clipboard, without anyone having to plug the phone
 * into a computer and pull logcat. Reachable from the start screen's own
 * overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(navController: NavController, viewModel: LogViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }

    // A plain periodic poll rather than a reactive flow: this is a read-only
    // diagnostic screen, not a latency-sensitive one, and re-reading a small
    // text file every couple seconds is cheap enough not to need more.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refresh()
            delay(2_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { copyLogToClipboard(context, viewModel.logText) }) {
                        Text("Copy")
                    }
                    TextButton(onClick = { showClearConfirm = true }) {
                        Text("Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = buildHeader(context),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = viewModel.logText.ifBlank { "No errors logged yet." },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear log?") },
            text = { Text("This deletes the on-device log file. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    showClearConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun copyLogToClipboard(context: Context, logText: String) {
    val report = buildHeader(context) + "\n\n" + logText.ifBlank { "No errors logged yet." }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Intelliverse log", report))
}

/**
 * Version/build type, device model/OS/ABI, and free/total RAM -- everything
 * needed to tell which build a pasted log came from and whether "no answer"
 * might just be a low-memory device struggling, without a separate "About
 * this build" screen to go find first.
 */
private fun buildHeader(context: Context): String {
    val memoryInfo = ActivityManager.MemoryInfo()
    runCatching {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memoryInfo)
    }
    fun gb(bytes: Long) = "%.1f GB".format(bytes / 1_000_000_000.0)

    val buildLine = "Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
        if (BuildConfig.DEBUG) "debug" else "release"
    val deviceLine = "Device: ${Build.MANUFACTURER} ${Build.MODEL}, " +
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
        Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    val ramLine = "RAM: ${gb(memoryInfo.availMem)} free / ${gb(memoryInfo.totalMem)} total"
    return listOf(buildLine, deviceLine, ramLine).joinToString("\n")
}
