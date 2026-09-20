package com.example.shared.presentation.screens.home

import android.content.Context
import android.widget.Toast
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shared.presentation.common.StaticLabelTextField
import com.example.shared.presentation.common.button.RoundIconButton
import com.example.shared.R.drawable
import com.example.shared.presentation.common.image.PictureItem

fun showWarning(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun checkImageValidity(
    selectedUris: List<Uri>,
    context: Context,
    viewModel: BaseHomeViewModel,
    corruptedUploadedFile: String,
    selectImageOrTextWarningMessage: String,
    expectImages: Boolean,
    onNavigate: () -> Unit
) {
    when {
        selectedUris.isNotEmpty() -> {
            val isUriValid = selectedUris.all { viewModel.checkUriValidity(it) }
            if (isUriValid) {
                onNavigate()
            } else {
                showWarning(context, corruptedUploadedFile)
            }
        }

        else -> {
            if (expectImages) {
                showWarning(context, selectImageOrTextWarningMessage)
            } else {
                onNavigate()
            }
        }
    }
}

fun onNextClick(
    userText: String,
    selectedUris: List<Uri>,
    context: Context,
    viewModel: BaseHomeViewModel,
    corruptedUploadedFile: String,
    selectImageOrTextWarningMessage: String,
    onNavigate: () -> Unit
) {
    if (userText.isBlank()) {
        checkImageValidity(
            selectedUris,
            context,
            viewModel,
            corruptedUploadedFile,
            selectImageOrTextWarningMessage,
            expectImages = true,
            onNavigate
        )
    } else {
        onNavigate()
    }
}

fun LazyListScope.homeInputSection(
    modifier: Modifier = Modifier,
    userText: String,
    onTextChange: (String) -> Unit,
    placeholderText: String,
    label: String,
    onCameraClick: () -> Unit,
    onUploadClick: () -> Unit,
    onEraseClick: () -> Unit,
    solveButton: @Composable () -> Unit
) {
    item {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StaticLabelTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp * 1.58f)
                    .padding(top = 10.dp * 1.58f),
                value = userText,
                onValueChange = onTextChange,
                placeholderText = placeholderText,
                label = label
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundIconButton(
                        modifier = Modifier.size(40.dp),
                        icon = drawable.ic_camera,
                        onButtonClick = onCameraClick
                    )
                    RoundIconButton(
                        modifier = Modifier.size(40.dp),
                        icon = drawable.ic_add_image,
                        onButtonClick = onUploadClick
                    )
                    RoundIconButton(
                        modifier = Modifier.size(40.dp),
                        icon = drawable.eraser,
                        onButtonClick = onEraseClick
                    )
                }

                solveButton()
            }
        }
    }
}

fun LazyListScope.imageGridItems(
    allUris: List<Uri>,
    selectedUris: List<Uri>,
    viewModel: BaseHomeViewModel,
    solveButton: @Composable () -> Unit
) {
    itemsIndexed(allUris) { _, imageUri ->
        val isSelected = selectedUris.contains(imageUri)

        val borderModifier = if (isSelected) Modifier.border(
            width = 4.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(16.dp)
        ) else Modifier

        PictureItem(
            imageModifier = borderModifier,
            imageUri = imageUri,
            onEnlarge = {
                viewModel.toggleSelectedUri(it)
                viewModel.updateIsImageEnlarged(it)
            },
            onRemove = {
                viewModel.removeImageFromTheList(imageUri)
                viewModel.removeUriFromSelection(imageUri)
            },
            onImageClick = viewModel::toggleSelectedUri
        )
    }

    if (allUris.size > 1) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                solveButton()
            }
        }
    }
}

@Composable
fun SettingsActionRow(
    onResetClick: () -> Unit,
    solveButton: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
            RoundIconButton(
                modifier = Modifier.size(40.dp),
                icon = drawable.eraser,
                onButtonClick = onResetClick
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        solveButton()
    }
}