package com.example.shared.domain.ai

/**
 * A well-formed answer that found nothing to work on -- no food on a photo
 * of food. Shown as that provider's failure, never as a solution: Groq
 * (#171) returned an empty food list with BMI 0.0 "Underweight", rendered as
 * if it were an analysis.
 */
class NothingRecognizedException(message: String) : Exception(message)
