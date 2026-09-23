package com.matterofchoice.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matterofchoice.model.Case
import kotlinx.serialization.Serializable

@Serializable
data class AnalysisResult(
    val overall_judgement: String,
    val cases: List<Case>
)

@Composable
fun AnalysisUI(analysisResult: AnalysisResult) {


    var parseError by remember { mutableStateOf<String?>(null) }




    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorScheme.surface
    ) {
        when {
            parseError != null -> {
                Text(
                    text = "Error.",
                    color = colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Overall Judgement",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4B3832)
                        )
                        Text(
                            text = analysisResult!!.overall_judgement,
                            color = Color.Black,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                    }

                    items(analysisResult!!.cases) { case ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Case:",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4B3832)
                                )
                                case.case_description?.let {
                                    Text(
                                        text = it,
                                        color = Color.Black,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                Text(
                                    text = "Player's Choice:",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4B3832)
                                )
                                case.player_choice?.let {
                                    Text(
                                        text = it,
                                        color = Color.Black,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                Text(
                                    text = "Optimal Choice:",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4B3832)
                                )
                                case.optimal_choice?.let {
                                    Text(
                                        text = it,
                                        color = Color.Black,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                Text(
                                    text = "Analysis:",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4B3832)
                                )
                                case.analysis?.let {
                                    Text(
                                        text = it,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}