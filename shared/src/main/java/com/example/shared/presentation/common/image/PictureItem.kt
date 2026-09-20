package com.example.shared.presentation.common.image

import android.net.Uri
import android.webkit.URLUtil
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.shared.R

@Composable
fun PictureItem(
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    imageUri: Uri,
    onRemove: () -> Unit,
    onEnlarge: (Uri) -> Unit,
    onImageClick: (Uri) -> Unit,
) {

    val buttonColor = Color.Black
    // var buttonColor by remember { mutableStateOf(Color.Black) }

    /*LaunchedEffect(imageUri) {
        val luminance = calculateImageLuminance(imageUri, context)
        buttonColor = if (luminance < 0.5) Color.White else Color.Black
    }*/

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
        ) {

            val imagePath = imageUri.toString()
            val isURL = URLUtil.isValidUrl(imagePath)
            val model: Any = if (isURL)
                ImageRequest.Builder(LocalContext.current)
                    .data(imagePath)
                    .build()
            else imageUri

            AsyncImage(
                modifier = modifier
                    .matchParentSize()
                    .clickable { onImageClick(imageUri) },
                model = model,
                contentDescription = "Picture",  // TODO { hardcoded string }
                error = painterResource(id = R.drawable.corrupted),
                placeholder = painterResource(id = R.drawable.loading_circle),
                contentScale = ContentScale.Crop,
            )

            Row(
                modifier = imageModifier
                    .padding(vertical = 40.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
                content = {

                    // Enlarge Button
                    IconButton(
                        modifier = Modifier
                            .background(
                                color = Color.White,
                                shape = CircleShape
                            ),
                        onClick = { onEnlarge(imageUri) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.enlarge_image),
                            contentDescription = "Enlarge", // TODO { hardcoded string }
                            tint = buttonColor
                        )
                    }

                    Spacer(modifier.weight(1f))

                    // Remove Button
                    IconButton(
                        modifier = Modifier
                            .background(
                                color = Color.White,
                                shape = CircleShape
                            ),
                        onClick = onRemove
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove", // TODO { hardcoded string }
                            tint = buttonColor
                        )
                    }
                }
            )
        }
    }
}