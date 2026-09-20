package com.example.shared.presentation.navigation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer


interface IScreen {
    val index: Int
    val labelId: Int
    val imageVector: Any

    fun createRoute(): String
    fun templateRoute(): String
}

fun sanitize(text: String): String {
    // Allow letters (from all the languages), digits, math symbols, and common punctuation
    return text.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{S}\\s%]"), "")
}

fun <T : IScreen> MutableList<T>.updateOrInsert(screen: T) {
    if (screen.index in indices) {
        this[screen.index] = screen
    } else {
        this.add(screen.index, screen)
    }
}

inline fun <reified T : IScreen> screenListSaverReified(): Saver<SnapshotStateList<T>, List<String>> {
    return screenListSaverUtil(serializer())
}

fun <T: IScreen> screenListSaverUtil(
    serializer: KSerializer<T>
): Saver<SnapshotStateList<T>, List<String>> {
    return Saver(
        save = { stateList -> stateList.map { Json.encodeToString(serializer, it) } },
        restore = { list ->
            SnapshotStateList<T>().apply {
                addAll(list.map { Json.decodeFromString(serializer, it) })
            }
        }
    )
}