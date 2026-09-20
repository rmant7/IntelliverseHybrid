package com.example.shared


enum class AppId(val id: String) {
    INTELLIVERSE("intelliverse"),
    SCHOOLKILLER("schoolkiller"),
    ONECLICKTRIP("oneclicktrip"),
    DIETTRACKER("diettracker"),
    STYLETRANSLATOR("styletranslator")
}

internal lateinit var googlePlayLink: String

internal lateinit var appName: String

fun setPropertiesByAppContext(appId: AppId) {
    appName = appId.id
    googlePlayLink = "https://play.google.com/store/apps/details?id=com.$appName"
}
