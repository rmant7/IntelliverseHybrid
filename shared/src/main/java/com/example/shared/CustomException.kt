package com.example.shared

object OutputSizeException: RuntimeException() {
    private fun readResolve(): Any = OutputSizeException
}

object UnableToAssistException: RuntimeException() {
    private fun readResolve(): Any = UnableToAssistException
}
/**
 * The user attached a photo but it could not be read (see ImageUtils' log
 * line for why). Answering without it would describe a photo nobody saw.
 */
class PhotoUnreadableException(
    message: String = "The attached photo could not be read, so it was not sent to any AI service",
) : RuntimeException(message)
