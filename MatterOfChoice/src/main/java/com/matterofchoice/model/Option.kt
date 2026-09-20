package com.matterofchoice.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Option(
    val number: Int,                  // The option number
    val option: String,               // The description of the option
    val health: Int,                  // Health impact of this option
    val wealth: Int,                  // Wealth impact of this option
    val relationships: Int,           // Relationship impact of this option
    val happiness: Int,               // Happiness impact of this option
    val knowledge: Int,               // Knowledge impact of this option
    val karma: Int,                   // Karma impact of this option
    val timeManagement: Int,         // Time management impact of this option
    val environmentalImpact: Int,    // Environmental impact of this option
    val personalGrowth: Int,         // Personal growth impact of this option
    val socialResponsibility: Int    // Social responsibility impact of this option
): Parcelable