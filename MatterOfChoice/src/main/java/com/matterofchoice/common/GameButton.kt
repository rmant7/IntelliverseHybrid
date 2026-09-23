package com.matterofchoice.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matterofchoice.ui.theme.MatterofchoiceTheme

@Composable
fun GameButton(
    onClick: () -> Unit,
    text: String,
    enabled: Boolean = true
) {


    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(top = 20.dp, bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    ) {
        Text(
            text = text, modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = 5.dp,
                bottom = 5.dp
            ),
            fontSize = 22.sp
        )


    }

}

@Preview
@Composable
fun MyButtonPrevieew(){
    MatterofchoiceTheme {
        GameButton(
            onClick = {},
            text = "Next"
        )
    }
}