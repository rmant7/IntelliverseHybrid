package com.intelliverse.presentation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.example.shared.log.AppLog
import com.example.shared.presentation.common.ApplicationScaffold
import com.intelliverse.BuildConfig
import com.intelliverse.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class StartViewModel @Inject constructor(
    private val appLog: AppLog,
    @ApplicationContext context: Context,
) : ViewModel() {
    /** Non-null exactly once, right after a cold start that followed a
     * notable abnormal exit -- see [AppLog.consumePendingCrashNotice]. */
    fun consumePendingCrashNotice(): String? = appLog.consumePendingCrashNotice()

    fun readLog(): String = appLog.readAll()

    private val whatsNewPrefs = context.getSharedPreferences("whats-new", Context.MODE_PRIVATE)

    /**
     * True exactly once per distinct build -- keyed on BuildConfig.BUILD_NUMBER
     * (the CI run number), not VERSION_CODE: this app ships many CI test
     * builds per Play versionCode bump (versionCode only changes right
     * before an actual Play upload), so a versionCode-keyed check would
     * never fire across a whole run of test builds sharing one versionCode
     * -- which is exactly what happened testing this. A real Play install
     * only ever corresponds to one specific BUILD_NUMBER per versionCode
     * anyway, so this doesn't change production behavior. Also true on the
     * very first launch ever on a device (nothing recorded yet) -- a fresh
     * install seeing this once is an acceptable trade for CI test builds
     * actually being able to show it.
     */
    fun consumeShouldShowWhatsNew(): Boolean {
        val lastSeen = whatsNewPrefs.getString(KEY_LAST_SEEN_BUILD, null)
        val current = BuildConfig.BUILD_NUMBER
        if (lastSeen == current) return false
        whatsNewPrefs.edit().putString(KEY_LAST_SEEN_BUILD, current).apply()
        return true
    }

    private companion object {
        const val KEY_LAST_SEEN_BUILD = "lastSeenBuildNumber"
    }
}


@Composable
fun StartScreen(
    navController: NavController,
    appDescriptions: Map<String, String>,
    viewModel: StartViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    // Offered once, right on the next launch after a crash/ANR/other
    // abnormal exit -- so sending a report is "tap Send, done" instead of
    // requiring anyone to already know a Log screen exists and go find it
    // themselves. Runs once per composition of this screen (LaunchedEffect
    // keyed on Unit), and AppLog.consumePendingCrashNotice() itself is a
    // one-shot read -- reopening this screen later in the same session
    // won't show it again.
    var crashNotice by remember { mutableStateOf<String?>(null) }
    // "What's new" only shows when there's no crash notice to show instead
    // this same launch -- a real crash report is more urgent than an
    // announcement, and stacking two AlertDialogs on the very first frame
    // is unpleasant either way.
    var showWhatsNew by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val notice = viewModel.consumePendingCrashNotice()
        crashNotice = notice
        if (notice == null) showWhatsNew = viewModel.consumeShouldShowWhatsNew()
    }
    val dietTracker = stringResource(com.diettracker.R.string.app_name_diet_tracker)
    val schoolKiller = stringResource(com.schoolkiller.R.string.app_name_schoolkiller)
    val styleTranslator = stringResource(com.styletranslator.R.string.app_name_styletranslator)
    val oneClickTrip = stringResource(com.oneclicktrip.R.string.app_name_oneclicktrip)
    val matterOfChoice = stringResource(com.matterofchoice.R.string.app_name_matter_of_choice)

    // Holds the currently selected app for displaying description
    val selectedApp = remember { mutableStateOf<String?>(null) }

    // List of apps to display
    val appButtons = listOf(
        Triple(oneClickTrip, R.drawable.oneclicktrip) { navController.navigate(oneClickTrip) },
        Triple(styleTranslator, R.drawable.styletranslator) { navController.navigate(styleTranslator) },
        Triple(dietTracker, R.drawable.diettracker) { navController.navigate(dietTracker) },
        Triple(schoolKiller, R.drawable.schoolkiller) { navController.navigate(schoolKiller) },
        Triple(matterOfChoice, R.drawable.matterofchoice) { navController.navigate(matterOfChoice) },
        Triple("CheapTrip", R.drawable.cheaptrip) { openCheapTripApp(context) }
    )

    var menuExpanded by remember { mutableStateOf(false) }

    ApplicationScaffold(isShowed = true, content = {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(R.string.welcome_to_intelliverse),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                items(appButtons) { (appName, iconResId, onClick) ->
                    AppButton(appName, iconResId, onClick) {
                        selectedApp.value = it
                    }
                }
            }

            // Overflow menu: host-level screens that don't belong to any one
            // mini-app -- Settings and Models are both empty shells for now,
            // Log is a way to see what went wrong without needing a computer
            // attached to pull logcat.
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            menuExpanded = false
                            navController.navigate("settings")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Models") },
                        onClick = {
                            menuExpanded = false
                            navController.navigate("models")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Log") },
                        onClick = {
                            menuExpanded = false
                            navController.navigate("log")
                        }
                    )
                }
            }
        }
    })

    // Show app description dialog
    selectedApp.value?.let { appName ->
        AlertDialog(
            onDismissRequest = { selectedApp.value = null },
            title = { Text(appName) },
            text = { Text(appDescriptions[appName] ?: "No description available.") },
            confirmButton = {
                Button(onClick = { selectedApp.value = null }) {
                    Text(stringResource(com.example.shared.R.string.Ok))
                }
            }
        )
    }

    crashNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { crashNotice = null },
            title = { Text("The app closed unexpectedly") },
            text = { Text("$notice\n\nSend a log to the developer so this can be fixed?") },
            confirmButton = {
                Button(onClick = {
                    sendLogsToDeveloper(context, viewModel.readLog())
                    crashNotice = null
                }) { Text("Send log") }
            },
            dismissButton = {
                TextButton(onClick = { crashNotice = null }) {
                    Text(stringResource(com.example.shared.R.string.cancel))
                }
            }
        )
    }

    if (showWhatsNew) {
        AlertDialog(
            onDismissRequest = { showWhatsNew = false },
            title = { Text(stringResource(R.string.whats_new_title)) },
            text = { Text(stringResource(R.string.whats_new_body)) },
            confirmButton = {
                Button(onClick = { showWhatsNew = false }) {
                    Text(stringResource(R.string.whats_new_got_it))
                }
            }
        )
    }
}

// Extracted for clarity
@Composable
private fun AppButton(
    appName: String,
    iconResId: Int,
    onButtonClick: () -> Unit,
    onInfoClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onButtonClick,
            modifier = Modifier
                .weight(1f)
                .height(100.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = "$appName Icon",
                    modifier = Modifier.size(48.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = appName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        IconButton(
            onClick = { onInfoClick(appName) },
            modifier = Modifier
                .size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info about $appName",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}


private fun openCheapTripApp(context: Context) {
    val packageName = "ru.z8.louttsev.bustrainflightmobile.androidApp"
    val mainActivity = "ru.z8.louttsev.bustrainflightmobile.androidApp.ui.MainActivity" // Exact activity name

    try {
        val intent = Intent().apply {
            component = ComponentName(packageName, mainActivity)
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER) // LAUNCHER category since it's an entry point
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        println("Failed to open the app: ${e.message}")

        // Fallback: Open Google Play Store if the app fails to launch
        val playStoreIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        )
        context.startActivity(playStoreIntent)
    }
}