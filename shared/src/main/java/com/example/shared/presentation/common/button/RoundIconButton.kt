package com.example.shared.presentation.common.button

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun RoundIconButton(
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier.size(30.dp).clip(CircleShape),
    icon: Any,
    onButtonClick: () -> Unit,
    isEnabled:Boolean = true,
    buttonColor: Color = MaterialTheme.colorScheme.primary
) {
    IconButton(
        modifier = modifier
            .clip(CircleShape)
            .size(50.dp),
            //.background(MaterialTheme.colorScheme.primary),
        onClick = { onButtonClick() },
        enabled = isEnabled,
        colors = IconButtonColors(
            containerColor = buttonColor,
            contentColor = MaterialTheme.colorScheme.secondary,
            disabledContainerColor = Color.Gray,
            disabledContentColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Icon(
            painter = when(icon){
                is Int -> painterResource(icon)
                is ImageVector -> rememberVectorPainter(icon)
                else -> rememberVectorPainter(Icons.Default.Close)
            },
            contentDescription = "App description",  // TODO { hardcode string }
            modifier = iconModifier,
            tint = MaterialTheme.colorScheme.background,
        )
    }
}