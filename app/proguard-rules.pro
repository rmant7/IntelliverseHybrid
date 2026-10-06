# R8 rules for the release build. Every rule below is here because of a
# specific, confirmed runtime lookup by name that R8 cannot see -- no
# blanket "-keep class **" for safety. Libraries that need rules (Hilt,
# Room, kotlinx.serialization >= 1.5, Gson's own TypeToken handling,
# coroutines, AppMetrica, play-services) ship them as consumer rules
# inside their own AARs/JARs; nothing is duplicated here for them.
#
# JNI entry points (Java_ai_localstudio_app_llama_LlamaBridge_native*) are
# kept by the engine's own consumer rules -- see below.

# --- SLF4J optional binding, not shipped -----------------------------------
# ktor-client-logging-jvm (a declared dependency in shared + 4 mini-apps,
# for Ktor's Logging plugin) pulls in slf4j-api. R8 (build #128) flagged
# org.slf4j.impl.StaticLoggerBinder as missing. Checked before suppressing:
#   - Nowhere in this codebase calls install(Logging) or references
#     Logger.SLF4J -- grepped the whole tree, zero matches. The Ktor class
#     that would trigger org.slf4j.LoggerFactory.getLogger() is therefore
#     never loaded at runtime; this app's actual logger is Timber.
#   - No SLF4J binding (logback-classic, slf4j-simple, slf4j-android) is
#     shipped -- there was never going to be a StaticLoggerBinder to find.
#   - Even if that Ktor class WERE reached, SLF4J 1.7.x's own
#     LoggerFactory.bind() catches NoClassDefFoundError internally and
#     falls back to a no-op logger -- this is its documented, intended
#     behavior without a binding on the classpath, not a bug.
# Dead code path referencing an optional class we don't ship, not a real
# missing dependency -- safe to silence.
-dontwarn org.slf4j.**

# Readable stack traces after retrace: keep line numbers, hide real
# source file names. Needed for the Log screen's emailed reports and for
# Play Console's crash deobfuscation alike.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- llama.cpp JNI ---------------------------------------------------------
# The engine (:llama-runtime, from rmant7/AI) ships its own consumer rules for
# what its JNI looks up by name (LlamaBridge$TokenSink.onToken, the native
# methods): llama-runtime/consumer-rules.pro. Release #160 crashed on the first
# local generation because the rules here still named the old
# com.intelliverse.llama package. CI checks the R8 mapping for it.

# --- MatterOfChoice: Gson reflection ----------------------------------------
# Gson maps JSON keys to Kotlin FIELD NAMES by reflection (none of these use
# @SerializedName), so the field names must survive. The classes themselves
# must be kept too: they're only ever instantiated by Gson (never by a
# constructor call R8 can see), and R8 full mode otherwise assumes a
# never-constructed class has no live instances and optimizes its field
# reads away. Only the types Gson actually (de)serializes -- confirmed
# against every fromJson/toJson call in FlaskApiClient.kt and
# GeminiRepository.kt:
#   fromJson: GenerateCasesResponse, StartAnalysisResponse,
#             AnalysisResultResponse (-> CaseAnalysis), List<Case> (-> Option)
#   toJson:   AnalysisRequest (-> Case -> Option), Map(... "options" -> Option)
# JobStatusResponse is built by hand from a JsonParser tree, not by Gson,
# and api.Option is never (de)serialized -- neither is listed.
-keep class com.matterofchoice.model.Case { <fields>; }
-keep class com.matterofchoice.model.Option { <fields>; }
-keep class com.matterofchoice.api.GenerateCasesResponse { <fields>; }
-keep class com.matterofchoice.api.StartAnalysisResponse { <fields>; }
-keep class com.matterofchoice.api.AnalysisResultResponse { <fields>; }
-keep class com.matterofchoice.api.CaseAnalysis { <fields>; }
-keep class com.matterofchoice.api.AnalysisRequest { <fields>; }
