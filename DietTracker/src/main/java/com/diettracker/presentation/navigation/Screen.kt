package com.diettracker.presentation.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.diettracker.R.string
import com.example.shared.R.drawable
import com.example.shared.presentation.navigation.IScreen
import com.example.shared.presentation.navigation.sanitize
import com.example.shared.presentation.navigation.screenListSaverReified
import kotlinx.serialization.Serializable

@Serializable
sealed class Screen: IScreen {

    @Serializable
    data object Home : Screen() {

        private val prefixRoute: String
            get() = "home"

        override fun createRoute(): String = prefixRoute
        override fun templateRoute(): String = prefixRoute

        override val labelId: Int
            get() = string.home
        override val imageVector: ImageVector
            get() = Icons.Filled.Home
        override val index: Int
            get() = 0
    }

    @Serializable
    data class Result(
        val passedImageUris: List<String>,
        val passedEditedResult: String,
        val userTask: String,
        val selectedLanguageIndex: Int,
        val secretShowAd: Boolean,
        val physicalActivity: String?,
        val gender: String?,
        val age: Int?,
        val height: Int?,
        val weight: Int?
    ) : Screen() {

        override fun createRoute(): String {
            val encodedUriList = passedImageUris.joinToString(",") { Uri.encode(it) }
            val path = listOf(
                encodedUriList,
                Uri.encode(passedEditedResult),
                Uri.encode(sanitize(userTask)),
                selectedLanguageIndex.toString(),
                secretShowAd.toString()
            ).joinToString("/")


            val queryParams = mutableListOf<String>()

            physicalActivity?.let { queryParams += "physicalActivity=$it" }
            gender?.let { queryParams += "gender=$it" }
            age?.let { queryParams += "age=$it" }
            height?.let { queryParams += "height=$it" }
            weight?.let { queryParams += "weight=$it" }

            return if (queryParams.isNotEmpty()) {
                "$prefixRoute/$path?${queryParams.joinToString("&")}"
            } else {
                "$prefixRoute/$path"
            }
        }

        override fun templateRoute(): String {
            val path = listOf(
                "passedImageUris",
                "passedEditedResult",
                "userTask",
                "selectedLanguageIndex",
                "secretShowAd"
            ).joinToString("/") { "{$it}" }
            val optionalParams = listOf(
                "physicalActivity",
                "gender",
                "age",
                "height",
                "weight"
            ).joinToString("&") { "$it={$it}" }

            return if (optionalParams.isNotEmpty()) {
                "$prefixRoute/$path?$optionalParams"
            } else {
                "$prefixRoute/$path"
            }
        }

        override val labelId: Int
            get() = string.results
        override val imageVector: Int
            get() = drawable.ic_ai_brain

        override val index: Int
            get() = 1

        companion object {
            val index: Int
                get() = 1
            val prefixRoute: String
                get() = "result"
        }
    }

    @Serializable
    data object Ocr : Screen() {

        private val prefixRoute: String
            get() = "ocr"

        override fun createRoute(): String = prefixRoute

        override fun templateRoute(): String = prefixRoute

        override val labelId: Int
            get() = string.ocr
        override val imageVector: Int
            get() = drawable.ic_document

        override val index: Int
            get() = 2
    }
}

val screenListSaver = screenListSaverReified<Screen>()