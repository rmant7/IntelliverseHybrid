package com.schoolkiller.presentation.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.schoolkiller.R.string
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
        val grade: Int,
        val detailsLevel: String,
        val selectedLanguageIndex: Int,
        val secretShowAd: Boolean
    ) : Screen() {

        override fun createRoute(): String {
            val encodedUriList = passedImageUris.joinToString(",") { Uri.encode(it) }
            return "$prefixRoute/$encodedUriList/${Uri.encode(passedEditedResult)}/${
                Uri.encode(
                    sanitize(userTask)
                )
            }/$grade/${Uri.encode(detailsLevel)}/$selectedLanguageIndex/$secretShowAd"
        }

        override fun templateRoute(): String {
            return "$prefixRoute/{passedImageUris}/{passedEditedResult}/{userTask}/{grade}/{detailsLevel}/{selectedLanguageIndex}/{secretShowAd}"
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

