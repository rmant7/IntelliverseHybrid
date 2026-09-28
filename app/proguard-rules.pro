# R8 rules for the release build. Every rule below is here because of a
# specific, confirmed runtime lookup by name that R8 cannot see -- no
# blanket "-keep class **" for safety. Libraries that need rules (Hilt,
# Room, kotlinx.serialization >= 1.5, Gson's own TypeToken handling,
# coroutines, AppMetrica, play-services) ship them as consumer rules
# inside their own AARs/JARs; nothing is duplicated here for them.
#
# JNI entry points (Java_com_intelliverse_llama_LlamaBridge_native*) are
# already covered by proguard-android-optimize.txt's own
# "-keepclasseswithmembernames class * { native <methods>; }" rule, which
# keeps both the class name and every native method name.

# Readable stack traces after retrace: keep line numbers, hide real
# source file names. Needed for the Log screen's emailed reports and for
# Play Console's crash deobfuscation alike.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- llama.cpp JNI callback ------------------------------------------------
# llama_jni.cpp calls back into Kotlin by NAME, three times:
#   env->GetMethodID(env->GetObjectClass(callback), "onToken", "(Ljava/lang/String;)V")
# Nothing on the Kotlin side ever calls onToken() itself, so without this
# R8 would treat it as dead code and remove it (or rename it) -- the native
# lookup then fails and the first local generation crashes natively.
-keep interface com.intelliverse.llama.LlamaBridge$TokenSink {
    void onToken(java.lang.String);
}
-keepclassmembers class * implements com.intelliverse.llama.LlamaBridge$TokenSink {
    void onToken(java.lang.String);
}

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
