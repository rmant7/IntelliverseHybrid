package com.example.shared

object OutputSizeException: RuntimeException() {
    private fun readResolve(): Any = OutputSizeException
}

object UnableToAssistException: RuntimeException() {
    private fun readResolve(): Any = UnableToAssistException
}