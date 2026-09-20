package com.matterofchoice

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.PagerState
import com.matterofchoice.ui.theme.MyColor
import com.matterofchoice.ui.theme.myFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalPagerApi::class)
@Composable
fun WelcomeFunction(navController: NavHostController) {

    val items = ArrayList<OnBoardData>()

    items.add(
        OnBoardData(
            R.drawable.w1,
            "Matter of Choice",
            "People make countless decisions daily. Are we equipped to make the best choices?"
        )
    )

    items.add(
        OnBoardData(
            R.drawable.w2,
            "Have fun",
            "What if children, while playing, would gain tools for real life decision making?"
        )
    )


    items.add(
        OnBoardData(
            R.drawable.w3,
            "Virtual Failures",
            "Usually we learn from negative consequences. Wouldn't it be better to learn from virtual failures?"
        )
    )

    val pagerState = rememberPagerState(
        pageCount = items.size,
        initialOffscreenLimit = 2,
        initialPage = 0,
        infiniteLoop = true
    )
    OnBoardingPager(
        item = items,
        pagerState = pagerState,
        navController
    )
}

@OptIn(ExperimentalPagerApi::class)
@Composable
private fun OnBoardingPager(
    item: List<OnBoardData>,
    pagerState: PagerState,
    navController: NavHostController
) {
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier
        .fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,

        ) {
            HorizontalPager(
                state = pagerState
            ) { page ->
                Column(
                    modifier = Modifier
                        .padding(45.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(item[page].image),
                        contentDescription = null,
                        modifier = Modifier
                            .height(380.dp)
                            .width(380.dp)
                    )


                    Text(
                        text = item[page].title,
                        modifier = Modifier.padding(top = 25.dp),
                        fontFamily = myFont,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = item[page].description,
                        modifier = Modifier.padding(
                            top = 15.dp,
                            start = 4.dp,
                            end = 4.dp,
                        ),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            PagerIndicator(item.size, pagerState.currentPage)
        }
        Column(
            modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 7.dp),
        ) {
            BottomSection(
                pagerState.currentPage,
                onNextClick = {
                    if (pagerState.currentPage < pagerState.pageCount - 1) {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                onSkipClick = {
                    coroutineScope.launch { pagerState.scrollToPage(pagerState.pageCount - 1) }
                }, navController = navController
            )

        }
    }

}


@Composable
fun PagerIndicator(
    size: Int,
    currentPage: Int
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.padding(bottom = 12.dp)

        ) {
        repeat(size) {
            Indicator(it == currentPage)
        }
    }
}

@Composable
fun Indicator(isSelected: Boolean) {
    val width =
        animateDpAsState(targetValue = if (isSelected) 25.dp else 10.dp, label = "WidthAnimation")
    Box(
        modifier = Modifier
            .padding(2.dp)
            .height(10.dp)
            .width(width.value)
            .clip(CircleShape)
            .background(
                if (isSelected) MyColor else Color.Gray.copy(alpha = 0.5f)
            )
    )

}


@OptIn(ExperimentalPagerApi::class)
@Composable
fun rememberPagerState(
    @IntRange(from = 0) pageCount: Int,
    @IntRange(from = 0) initialPage: Int = 0,
    @FloatRange(from = 0.0, to = 1.0) initialPageOffset: Float = 0f,
    @IntRange(from = 1) initialOffscreenLimit: Int = 1,
    infiniteLoop: Boolean = false
): PagerState = rememberSaveable(
    saver = PagerState.Saver
) {
    PagerState(
        pageCount = pageCount,
        currentPage = initialPage,
        currentPageOffset = initialPageOffset,
        offscreenLimit = initialOffscreenLimit,
        infiniteLoop = infiniteLoop
    )
}


@Composable
fun BottomSection(
    currentPage: Int,
    onNextClick: () -> Unit,
    onSkipClick: () -> Unit,
    navController: NavController
) {

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = if (currentPage != 2) Arrangement.SpaceBetween else Arrangement.Center
    ) {
        if (currentPage == 2) {
            Button(
                onClick = {
                    navController.navigate(Screens.GameScreen.screen)
                },
                shape = RoundedCornerShape(8),
                colors = buttonColors(MyColor)
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 20.sp,
                    modifier = Modifier
                        .padding(end = 50.dp, bottom = 10.dp, top = 10.dp, start = 56.dp),
                    color = Color.White
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = Color.White
                )
            }
        } else {
            OutlinedButton(
                modifier = Modifier
                    .width(80.dp)
                    .height(66.dp),
                onClick = {
                    onSkipClick()
                },
                border = BorderStroke(width = 2.dp, color = MyColor),
                shape = RoundedCornerShape(5.dp)
            ) {
                Text(text = "Skip", color = MyColor)
            }
            NextButton(onClick = { onNextClick() })
        }
    }
}

@Composable
fun NextButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier
            .width(80.dp)
            .height(66.dp),
        onClick = { onClick() },
        colors = buttonColors(MyColor),
        shape = RoundedCornerShape(5.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.White
        )
    }
}

data class OnBoardData(
    val image: Int,
    val title: String,
    val description: String
)