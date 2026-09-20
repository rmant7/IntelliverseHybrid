package com.styletranslator.presentation.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.styletranslator.R.string
import com.example.shared.R.drawable
import com.example.shared.presentation.navigation.IScreen
import com.example.shared.presentation.navigation.sanitize
import com.example.shared.presentation.navigation.screenListSaverReified
import kotlinx.serialization.Serializable
import java.util.Locale


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
        val transformationLevel: String,
        val selectedLanguageIndex: Int,
        val secretShowAd: Boolean,
        val sourceGender: String?,
        val targetGender: String?,
        val sourceAge: Int?,
        val targetAge: Int?,
        val category: String?,
        val style: String?,
        val mentality: String?,
        val tonePreference: String?,
        val translationScale: Float?
    ) : Screen() {

        override fun createRoute(): String {
            val encodedImageUris = passedImageUris.joinToString(",") { Uri.encode(it) }
            val path = listOf(
                encodedImageUris,
                Uri.encode(passedEditedResult),
                Uri.encode(sanitize(userTask)),
                transformationLevel,
                selectedLanguageIndex.toString(),
                secretShowAd.toString()
            ).joinToString("/")

            val queryParams = mutableListOf<String>()

            sourceGender?.let { queryParams += "sourceGender=$it" }
            targetGender?.let { queryParams += "targetGender=$it" }
            sourceAge?.let { queryParams += "sourceAge=$it" }
            targetAge?.let { queryParams += "targetAge=$it" }
            category?.let { queryParams += "category=$it" }
            style?.let { queryParams += "style=$it" }
            mentality?.let { queryParams += "mentality=$it" }
            tonePreference?.let { queryParams += "tonePreference=$it" }
            translationScale?.let {
                val formatted = "%.2f".format(Locale.US, it)
                queryParams += "translationScale=$it"
            }

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
                "transformationLevel",
                "selectedLanguageIndex",
                "secretShowAd"
            ).joinToString("/") { "{$it}" }

            val optionalParams = listOf(
                "sourceGender",
                "targetGender",
                "sourceAge",
                "targetAge",
                "category",
                "style",
                "mentality",
                "tonePreference",
                "translationScale"
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