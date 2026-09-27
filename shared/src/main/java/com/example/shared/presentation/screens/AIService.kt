package com.example.shared.presentation.screens

enum class AIService {
    GEMINI,
    GEMINI_THINKING,
    GPT,
    GROQ,
    GIGACHAT,
    // On-device local models -- only ever produced by a sub-app that
    // overrides BaseResultViewModel's additionalProviderCount()/
    // launchAdditionalProviders() (StyleTranslator); every other sub-app's
    // maxSolutionResultsCapacity never counts these, so they simply never
    // appear in its solutionResults map.
    LOCAL_TRANSLATEGEMMA,
    LOCAL_OMNITRANSLATE,
    LOCAL_MADLAD,
}