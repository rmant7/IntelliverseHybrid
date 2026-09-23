package com.matterofchoice.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matterofchoice.R
import com.matterofchoice.ui.theme.MatterofchoiceTheme
import com.matterofchoice.ui.theme.myFont

@Composable
fun Result() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)


    val userScore = sharedPreferences.getInt("userScore", 0)
    val totalScore = sharedPreferences.getInt("totalScore", 0)
    val rounds = sharedPreferences.getInt("rounds", 0)

    Log.v("USERSCORE", userScore.toString())
    Log.v("TOTALSCORE", totalScore.toString())


    Column(
        modifier = Modifier
            .background(colorScheme.surface)
            .fillMaxSize()
            .padding(top = 45.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {


        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier.fillMaxWidth()
        ) {


            Text(
                modifier = Modifier.padding(bottom = 20.dp),
                text = rounds.toString(),
                fontSize = 32.sp
            )
            Image(
                painterResource(R.drawable.w1),
                contentDescription = null,
                modifier = Modifier
                    .size(150.dp),
                contentScale = ContentScale.Crop

            )
            Text(
                text = stringResource(R.string.rounds),
                fontSize = 32.sp,
                modifier = Modifier.padding(top = 10.dp)
            )

            Text(
                modifier = Modifier.padding(top = 55.dp),
                text = "$userScore/$totalScore",
                fontSize = 38.sp,
                fontFamily = myFont,
            )
            Spacer(
                modifier = Modifier
                    .height(1.dp)
                    .width(130.dp)
                    .background(Color.Black)
            )

        }

        Column(
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.score_calculate_based_on_health_wealth_relationships_happiness_knowledge_karma_time_management_environmental_impact_personal_growth_and_social_responsibility),
                textAlign = TextAlign.Justify,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
                    .padding(bottom = 4.dp)
            )
        }
    }

}


@Preview(showBackground = true)
@Composable
fun MyPreView() {
    MatterofchoiceTheme {
        Result()
    }
}