package com.example.shared.presentation.common.button

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun RadioIndexButton(
    isSelected:Boolean,
    isEnabled: Boolean,
    onRadioButtonClick: () -> Unit
) {
    //val isSelected = currentIndex == index
    val isClicked = remember { mutableStateOf(isSelected) }
    if (!isEnabled && !isSelected) isClicked.value = false

    val defaultButtonColor = MaterialTheme.colorScheme.secondary
    val animatedColor = remember { Animatable(defaultButtonColor) }

    LaunchedEffect(isEnabled && !isClicked.value) {
        repeat(5) { // Number of flickers
            if (isClicked.value) {
                animatedColor.animateTo(defaultButtonColor)
                return@repeat // return from current loop if button is selected
            }
            animatedColor.animateTo(
                targetValue = Color.Green,
                animationSpec = tween(300) // Duration for each flicker
            )
            animatedColor.animateTo(
                targetValue = defaultButtonColor,
                animationSpec = tween(300)
            )
        }
    }

    fun onClick() {
        isClicked.value = true
        onRadioButtonClick()
    }

    /*
    @Composable
    fun getUnselectedButtonColor(): Color {
        return if (isEnabled && !isClicked.value && !isSelected) Color.Yellow
        else MaterialTheme.colorScheme.secondary
    }
    */

    RadioButton(
        selected = isSelected,
        onClick = { onClick() },
        enabled = isEnabled,
        colors = RadioButtonDefaults.colors(
            unselectedColor = animatedColor.value,
        )
    )

}

@Composable
fun FlickeringRoundIconButton(
    icon: Int,
    isEnabled: Boolean,
    onButtonClick: () -> Unit,
    hasFlickered: Boolean,
    updateHasFlickered: (Boolean) -> Unit
) {
    val defaultButtonColor = MaterialTheme.colorScheme.primary
    val animatedColor = remember { Animatable(defaultButtonColor) }

    LaunchedEffect(isEnabled) {
        if (isEnabled && !hasFlickered) {
            updateHasFlickered(true)
            repeat(10) {
                animatedColor.animateTo(
                    targetValue = Color.Green,
                    animationSpec = tween(300)
                )
                animatedColor.animateTo(
                    targetValue = defaultButtonColor,
                    animationSpec = tween(300)
                )
            }
        }
    }

    RoundIconButton(
        icon = icon,
        isEnabled = isEnabled,
        buttonColor = animatedColor.value,
        onButtonClick = onButtonClick
    )
}
