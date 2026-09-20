package com.example.shared.domain.prompt.options

import android.content.Context
import com.example.shared.R

enum class Mentality(private val arrayIndex: Int, val mentality: String) {

    RUSSIAN(0, "collectivist"),
    EAST_EUROPEAN(1, "pragmatic"),
    WEST_EUROPEAN(2, "individualistic"),
    SOUTHEAST_ASIAN(3, "harmony-focused"),
    CHINESE(4, "hierarchical"),
    CAUCASIAN(5, "honor-bound"),
    NORTH_AMERICAN(6, "entrepreneurial"),
    LATIN_AMERICAN(7, "passionate"),
    MIDDLE_EASTERN(8, "hospitality-driven"),
    AFRICAN(9, "communal"),
    NORDIC(10, "egalitarian"),
    SOUTH_ASIAN(11, "family-centric"),
    JAPANESE(12, "group harmony"),
    AUSTRALIAN(13, "laid-back"),
    MEDITERRANEAN(14, "expressive"),
    CENTRAL_ASIAN(15, "nomadic traditions"),
    GERMANIC(16, "structured"),
    EASTERN_EUROPEAN_BALKAN(17, "resilient"),
    ARCTIC_INDIGENOUS(18, "nature-connected"),
    JEWISH(19, "community-oriented");

    fun getString(context: Context): String {
        val explanationsArray = context.resources.getStringArray(R.array.mentalities)
        return explanationsArray[arrayIndex]
    }
}