package com.oneclicktrip.presentation.screens.output.result

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class MidwayStop(
    val name: String,
    val description: String,
    val time: String,
    val links: List<String> = emptyList(),
    val tips: List<String> = emptyList()
)

@Serializable
data class Activity(
    val name: String,
    val description: String,
    val time: String,
    // Confirmed on a real device (GigaChat): the model occasionally omits
    // one of these fields entirely for one activity in a long itinerary
    // (first activityCost, then tips on a later run) rather than emitting
    // an empty value -- defaulting all three the same way avoids a hard
    // decode failure (MissingFieldException) that otherwise discarded the
    // whole response over one field missing on one activity.
    val links: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val activityCost: String = "",
    val midwayStops: List<MidwayStop>? = null
)

@Serializable
data class Accommodation(
    val name: String,
    val link: String
)

@Serializable
data class Day(
    val activities: List<Activity>,
    val accommodations: List<Accommodation>
)

@Serializable
data class TripSummary(
    val totalCost: String,
    val visitedDestinations: String,
    val transitTime: String,
    val transportTypes: String,
    val lodgingTypes: String,
    val categories: String
)

@Serializable
data class TripSolutionResponse(
    val titles: Map<String, String>,
    val days: List<Day>,
    val summary: TripSummary,
    val ocrText: String? = null,
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Confirmed on a real device (Groq): a model occasionally quotes a proper
 * noun inline inside a string value without escaping it, e.g.
 * `"...знаменитым "Голова Джека". Прогулка..."` -- the unescaped inner
 * quote ends the JSON string early and leaves the rest unparsable
 * ("Expected quotation mark, but had 'Г' instead").
 *
 * Repaired with a small state-machine scan rather than a regex: walk the
 * text tracking whether the cursor is inside a string, and for every `"`
 * encountered while inside one, look at the next non-whitespace character.
 * A real closing quote is always followed by a structural character
 * (`,`, `}`, `]`, `:`, or end of input); anything else means this quote is
 * embedded prose, not a terminator, so it gets escaped in place instead.
 * Must run after the ';'->',' repair below, not before: that repair fixes
 * a genuine string terminator that would otherwise look identical to a
 * stray embedded quote to this scan (both are followed by a non-structural
 * character -- ';' here, the next key's own text there).
 */
private fun repairUnescapedInnerQuotes(input: String): String {
    val sb = StringBuilder(input.length + 16)
    var inString = false
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (inString) {
            when (c) {
                '\\' -> {
                    sb.append(c)
                    if (i + 1 < input.length) {
                        sb.append(input[i + 1])
                        i++
                    }
                }
                '"' -> {
                    var j = i + 1
                    while (j < input.length && input[j].isWhitespace()) j++
                    val next = input.getOrNull(j)
                    if (next == null || next in charArrayOf(',', '}', ']', ':')) {
                        inString = false
                        sb.append(c)
                    } else {
                        sb.append("\\\"")
                    }
                }
                else -> sb.append(c)
            }
        } else {
            sb.append(c)
            if (c == '"') inString = true
        }
        i++
    }
    return sb.toString()
}

/**
 * Confirmed on a real device: the model occasionally omits one or more
 * keys from its own `titles` map (a real label for the field is supposed
 * to be there in whatever language it generated the response in), which
 * showed up as literal "null:" text in front of the value it labels --
 * Kotlin renders a null Map lookup as that string in an interpolated
 * template. Falling back to the field's own raw key name at least keeps
 * this readable, rather than a bare "null".
 */
private fun Map<String, String>.labelOrKey(key: String): String = this[key] ?: key

fun decodeTripSolutionResponse(jsonResponse: String): Pair<String, String> {
    val cleanedJson = jsonResponse.trim()
        .removeSurrounding("```json", "```")
        .trim()
        // Confirmed on a real device (GigaChat): a model occasionally emits
        // ';' where JSON requires ',' between a closing quote and the next
        // key/value's opening quote (e.g. `"...холма.";\n  "time": ...`
        // instead of `"...холма.",`). Safe to repair unconditionally,
        // unlike ':' or ',' themselves: ';' is never a valid JSON
        // structural character in ANY position (key/value separator is
        // always ':', element/member separator always ','), so there's no
        // legitimate case this could be mis-firing on.
        .replace(Regex("\";(\\s*)\""), "\",$1\"")
        .let(::repairUnescapedInnerQuotes)

    val solution: TripSolutionResponse = json.decodeFromString(cleanedJson)

    val titles = solution.titles
    return buildString {
        appendLine("📋 ${titles.labelOrKey("summary")}:")
        appendLine("  - ${titles.labelOrKey("totalCost")}: ${solution.summary.totalCost}")
        appendLine("  - ${titles.labelOrKey("visitedDestinations")}: ${solution.summary.visitedDestinations}")
        appendLine("  - ${titles.labelOrKey("transitTime")}: ${solution.summary.transitTime}")
        appendLine("  - ${titles.labelOrKey("transportTypes")}: ${solution.summary.transportTypes}")
        appendLine("  - ${titles.labelOrKey("lodgingTypes")}: ${solution.summary.lodgingTypes}")
        appendLine("  - ${titles.labelOrKey("categories")}: ${solution.summary.categories}")
        appendLine()
        solution.days.forEachIndexed { index, day ->
            appendLine("📅 ${titles.labelOrKey("day")} ${index + 1}:")

            appendLine("  🔸 ${titles.labelOrKey("activities")}:")
            day.activities.forEach { activity ->
                appendLine("    • ${activity.name}")
                appendLine("      - ${titles.labelOrKey("description")}: ${activity.description}")
                appendLine("      - ${titles.labelOrKey("time")}: ${activity.time}")
                appendLine("      - ${titles.labelOrKey("activityCost")}: ${activity.activityCost}")

                if (activity.links.isNotEmpty()) {
                    appendLine("      - ${titles.labelOrKey("links")}:")
                    activity.links.forEach { link ->
                        appendLine("          🔗 $link")
                    }
                }

                if (activity.tips.isNotEmpty()) {
                    appendLine("      - ${titles.labelOrKey("tips")}:")
                    activity.tips.forEach { tip ->
                        appendLine("          💡 $tip")
                    }
                }

                // 🔥 NEW: handle midway stops if exist
                if (!activity.midwayStops.isNullOrEmpty()) {
                    appendLine("      - ${titles.labelOrKey("midwayStops")}:")
                    activity.midwayStops.forEach { stop ->
                        appendLine("          🛑 ${stop.name}")
                        appendLine("             - ${titles.labelOrKey("description")}: ${stop.description}")
                        appendLine("             - ${titles.labelOrKey("time")}: ${stop.time}")

                        if (stop.links.isNotEmpty()) {
                            appendLine("             - ${titles.labelOrKey("links")}:")
                            stop.links.forEach { link ->
                                appendLine("                 🔗 $link")
                            }
                        }

                        if (stop.tips.isNotEmpty()) {
                            appendLine("             - ${titles.labelOrKey("tips")}:")
                            stop.tips.forEach { tip ->
                                appendLine("                 💡 $tip")
                            }
                        }
                    }
                }
            }

            if (day.accommodations.isNotEmpty()) {
                appendLine("  🏨 ${titles.labelOrKey("accommodations")}:")
                day.accommodations.forEach { acc ->
                    appendLine("    🏨 ${acc.name} → ${acc.link}")
                }
            }

            appendLine()
        }
    } to (solution.ocrText ?: "")
}