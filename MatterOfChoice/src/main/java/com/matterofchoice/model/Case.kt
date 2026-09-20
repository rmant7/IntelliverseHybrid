package com.matterofchoice.model


data class Case(
    val case_id: String,
    val case: String,
    val options: List<Option>,
    val optimal: String,
    val turn: Int? = null,
    val user_answer: String? = null,

    // Fields for analysis results (may be null)
    val case_description: String? = null,
    val player_choice: String? = null,
    val optimal_choice: String? = null,
    val analysis: String? = null
)