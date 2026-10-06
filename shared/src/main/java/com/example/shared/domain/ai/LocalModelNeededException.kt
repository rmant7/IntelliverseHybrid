package com.example.shared.domain.ai

/**
 * A photo run with no on-device model that can take it now. Photos are
 * answered on the phone only, so this is the run's whole answer: the result
 * screen offers which model to get (see LocalModelAdvice).
 */
class LocalModelNeededException(message: String) : Exception(message)
