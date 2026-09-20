package com.example.shared.presentation.common.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.shared.OutputSizeException
import com.example.shared.R
import com.example.shared.UnableToAssistException
import dev.ai4j.openai4j.OpenAiHttpException
import io.ktor.client.plugins.ServerResponseException
import timber.log.Timber
import java.net.UnknownHostException

@Composable
fun ErrorAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    errors: List<Throwable>,
    icon: ImageVector,
) {
    val dialogData = getAlertWindowData(errors)

    if (dialogData != null) {
        AlertDialog(
            icon = {
                Icon(icon, contentDescription = "Icon")
            },
            title = {
                Text(text = dialogData.first)
            },
            text = {
                Text(text = dialogData.second)
            },
            onDismissRequest = {
                onDismissRequest()
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmation()
                    }
                ) {
                    Text(stringResource(R.string.Ok))
                }
            }
        )
    }
}

@Composable
private fun getAlertWindowData(errorsList: List<Throwable>): Pair<String, String>? {

    //val errorMessage = StringBuilder()
    val errorIds = mutableListOf<Int>()
    errorsList.toSet() // Remove duplicates
        .forEach { error ->
            // errors we do not display to the user
            if (error is Exception && (error == OutputSizeException || error == UnableToAssistException || findNestedOpenAIException(error))) {
                Timber.e("Internal exception: $error")
                return@forEach
            } else {
                val errorId = when (error) {
                    is ServerResponseException -> R.string.error_service_not_available
                    // is UnknownHostException -> R.string.error_service_not_available
                    // is IOException -> R.string.error_service_not_available
                    // get other errors messages here
                    else -> R.string.error_common_message
                }
                errorIds.add(errorId)
            }
        }
    errorIds.toSet()
        .forEach {
            //errorMessage.append("${stringResource(it)}\n")
            Timber.e("${stringResource(it)}\n")
        }
    return null // for now, this error handling section is not stable, especially when we use
// something wierd such as ResultViewModel.maxSolutionResultsCapacity
    /*if (errorIds.isEmpty()) {
        return null
    }

    errorIds.toSet()
        .forEach {
            errorMessage.append("${stringResource(it)}\n")
        }
    return stringResource(R.string.error_common_title) to errorMessage.toString()*/
}

fun findNestedOpenAIException(exception: Exception): Boolean {
    return exception is OpenAiHttpException || exception is UnknownHostException || (exception.cause as? Exception)?.let {
        findNestedOpenAIException(
            it
        )
    } == true
}
