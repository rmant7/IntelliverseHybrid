package com.oneclicktrip.presentation.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.oneclicktrip.R.string
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
        val originLocation: String,
        val cityPaths: List<String>,
        val transportationTypes: List<String>,
        val tripStyles: List<String>,
        val oneWay: Boolean,
        val secretShowAd: Boolean,
        val maxBudget: Int?,
        val tripDuration: Int?,
        val travelersNumber: Int?
    ) : Screen() {

        override fun createRoute(): String {
            val encodedImageUris = passedImageUris.joinToString(",") { Uri.encode(it) }
            val encodedCityPaths = cityPaths.joinToString(",") { Uri.encode(it) }
            val encodedTransportationTypes = transportationTypes.joinToString(",") { Uri.encode(it) }
            val encodedTripStyles = tripStyles.joinToString(",") { Uri.encode(it) }
            val path = listOf(
                encodedImageUris,
                Uri.encode(passedEditedResult),
                Uri.encode(sanitize(userTask)),
                selectedLanguageIndex.toString(),
                Uri.encode(sanitize(originLocation)),
                encodedCityPaths,
                encodedTransportationTypes,
                encodedTripStyles,
                oneWay.toString(),
                secretShowAd.toString()
            ).joinToString("/")

            val queryParams = mutableListOf<String>()

            maxBudget?.let { queryParams += "maxBudget=$it" }
            tripDuration?.let { queryParams += "tripDuration=$it" }
            travelersNumber?.let { queryParams += "travelersNumber=$it" }

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
                "originLocation",
                "cityPaths",
                "transportationTypes",
                "encodedTripStyles",
                "oneWay",
                "secretShowAd"
            ).joinToString("/") { "{$it}" }

            val optionalParams = listOf(
                "maxBudget",
                "tripDuration",
                "travelersNumber"
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