package com.matterofchoice//package com.matterofchoice
//
//import android.app.Application
//import android.content.Context
//import android.util.Log
//import androidx.compose.runtime.State
//import androidx.compose.runtime.mutableStateOf
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.viewModelScope
//import com.google.ai.client.generativeai.GenerativeModel
//import kotlinx.coroutines.launch
//import java.io.File
//import java.io.IOException
//
//class AnalysisViewModel(application: Application) : AndroidViewModel(application = application) {
//
//    private val model = GenerativeModel(
//        modelName = "gemini-2.5-flash",
//        apiKey = "...",
//    )
//
//    private val sharedPreferences =
//        application.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
//
//
//    private var _state = mutableStateOf(AnalysisState())
//    val state: State<AnalysisState> = _state
//
//
//    fun loadAnalysis(context: Context, role: String) {
//        val outputPath = context.getExternalFilesDir("tool")?.absolutePath ?: ""
//        var userChoices = ""
//
//        val language = sharedPreferences.getString("userLanguage", "English")
//        _state.value = _state.value.copy(isLoading = true)
//
//        if (outputPath.isEmpty()) {
//            Log.e("OUTPUT_PATH", "Failed to get app-specific external storage directory")
//            _state.value = _state.value.copy(error = "No data to analysis")
//            _state.value = _state.value.copy(isLoading = false)
//        } else {
//            val outputDirectory = File(outputPath)
//            if (outputDirectory.exists() && outputDirectory.isDirectory) {
//                val files = outputDirectory.listFiles() // Get all files in the directory
//                if (files != null && files.isNotEmpty()) {
//                    for (file in files) {
//                        if (file.isFile) { // Check if it's a file
//                            try {
//                                val fileContents = file.readText()
//                                userChoices += fileContents
//                                Log.v("FILE_CONTENTS", "Contents of ${file.name}: $fileContents")
//                            } catch (e: IOException) {
//                                Log.e("FILE_READ", "Failed to read file ${file.name}: ${e.message}")
//
//                                _state.value = _state.value.copy(error = "No data to analysis")
//                                _state.value = _state.value.copy(isLoading = false)
//                            }
//                        }
//                    }
//                } else {
//                    Log.v("DIRECTORY", "No files found in the directory.")
//                    _state.value = _state.value.copy(error = "No data to analysis")
//                    _state.value = _state.value.copy(isLoading = false)
//                }
//            } else {
//                Log.e("DIRECTORY", "Output directory does not exist or is not a directory.")
//                _state.value = _state.value.copy(error = "No data to analysis")
//                _state.value = _state.value.copy(isLoading = false)
//            }
//        }
//
//        val prompt = """
//            Analyze the following data. You are a $role.  The data contains a series of cases, each with a question, options, and the player's chosen answer (indicated by the 'answer' key). The 'optimal' key indicates the correct option.  Determine the language used in the data and STRICTLY PROVIDE YOUR ANALYSIS IN THE LANGUAGE $language.  Format your response as JSON:
//
//            {{
//              "overall_judgement": "A concise summary of the player's overall {judgement_aspect}.",
//              "cases": [
//                {{
//                  "case_description": "The case description.",
//                  "player_choice": "The player's selected option in words.",
//                  "optimal_choice": "The optimal option in words.",
//                  "analysis": "A detailed analysis of the player's choice, including reasoning and implications."
//                }}
//              ]
//            }}
//
//            Data: $userChoices
//
//
//        """.trimIndent()
//
//        viewModelScope.launch {
//            try {
//                val analysis =
//                    model.generateContent(
//                        prompt
//                    )
//                if (analysis.text != null) {
//                    _state.value = _state.value.copy(analysis = analysis.text)
//                }
//                _state.value = _state.value.copy(isLoading = false)
//            } catch (e: Exception) {
//                _state.value = _state.value.copy(error = e.message.toString())
//                _state.value = _state.value.copy(isLoading = false)
//            }
//        }
//    }
//}