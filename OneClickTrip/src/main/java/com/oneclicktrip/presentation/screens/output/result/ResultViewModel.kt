package com.oneclicktrip.presentation.screens.output.result

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.shared.domain.usecases.SpeechConverter
import com.example.shared.domain.usecases.ai.client.GeminiUseCaseClient
import com.example.shared.domain.usecases.ai.GigaChatUseCase
import com.example.shared.domain.usecases.ai.GroqUseCase
import com.example.shared.domain.usecases.ai.OpenAiUseCase
import com.example.shared.domain.usecases.AudioPlayer
import com.example.shared.domain.usecases.ImageUtils
import com.example.shared.ads.InterstitialAdUseCase
import com.example.shared.presentation.screens.output.result.BaseResultViewModel
import com.example.shared.presentation.screens.output.result.doubleQuotes
import com.example.shared.presentation.screens.output.result.jsonResponseLanguage
import com.example.shared.presentation.screens.output.result.ocrTextJsonEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
class ResultViewModel @Inject constructor(
    imageUtils: ImageUtils,
    geminiUseCaseClient: GeminiUseCaseClient,
    openAiUseCase: OpenAiUseCase,
    groqUseCase: GroqUseCase,
    gigaChatUseCase: GigaChatUseCase,
    interstitialAdUseCase: InterstitialAdUseCase,
    speechConverter: SpeechConverter,
    audioPlayer: AudioPlayer,
    savedStateHandle: SavedStateHandle
) : BaseResultViewModel(imageUtils, geminiUseCaseClient, openAiUseCase, groqUseCase, gigaChatUseCase, interstitialAdUseCase, speechConverter, audioPlayer, savedStateHandle) {

    override val audioPrefixName: String
        get() = "oneclicktrip"
    private var originLocation: String = ""
    private var cityPaths: List<String>
    private var transportationTypes: List<String>
    private var tripStyles: List<String>
    private var maxBudget: Int? = null
    private var tripDuration: Int? = null
    private var travelersNumber: Int? = null
    private var oneWay: Boolean = false

    init {
        savedStateHandle.get<String>("originLocation")?.let { originLocation = it }
        savedStateHandle.get<Boolean>("oneWay")?.let { oneWay = it }

        val encodedCityPaths = savedStateHandle.get<String>("cityPaths")
        cityPaths = encodedCityPaths
            ?.split(",")
            ?.mapNotNull { encoded -> Uri.decode(encoded).takeIf { it.isNotBlank() } }
            ?: emptyList()

        val encodedTransportationTypes = savedStateHandle.get<String>("transportationTypes")
        transportationTypes = encodedTransportationTypes
            ?.split(",")
            ?.mapNotNull { encoded -> Uri.decode(encoded).takeIf { it.isNotBlank() } }
            ?: emptyList()

        val encodedTripStyles = savedStateHandle.get<String>("tripStyles")
        tripStyles = encodedTripStyles
            ?.split(",")
            ?.mapNotNull { encoded -> Uri.decode(encoded).takeIf { it.isNotBlank() } }
            ?: emptyList()

        savedStateHandle.get<String?>("maxBudget")?.toIntOrNull()?.let { maxBudget = it }
        savedStateHandle.get<String?>("tripDuration")?.toIntOrNull()?.let { tripDuration = it }
        savedStateHandle.get<String?>("travelersNumber")?.toIntOrNull()?.let { travelersNumber = it }

        fun buildSolvingPrompt(): String {
            val description = if (imageUsed) {
                "Trip descriptions are provided in the attached images."
            } else if (passedEditedResult.isNotBlank()) {
                "${addText(passedEditedResult, additional = false)}\n\n${addText(userTask, additional = true)}"
            } else {
                addText(userTask, additional = false)
            }
            return """

        You are a professional travel planner AI assistant. Using the input parameters below, generate a highly detailed and personalized travel itinerary.

        ### Input Parameters:
        ${if (description.isNotBlank()) "- Trip description: $description" else ""}
        ${"- Solution language: ${selectedLanguage.languageName}"}
        ${if (originLocation.isNotBlank()) "- Origin location: $originLocation" else ""}
        ${cityPaths.takeIf { it.isNotEmpty() }?.let { "- Desired locations to visit: ${it.joinToString()}" } ?: ""}
        ${"- Trip type: ${if (oneWay) "One-way (no return to origin location at the end)." else "Round-trip (return to origin location at the end)."}"}
        ${transportationTypes.takeIf { it.isNotEmpty() }?.let { "- Preferred transportation methods: ${it.joinToString()}. Use these methods whenever possible; Only suggest alternative transportation if it is completely impractical or impossible to use the preferred methods." } ?: ""}
        ${tripStyles.takeIf { it.isNotEmpty() }?.let { "- Trip style preferences: ${it.joinToString()}." } ?: ""}
        ${travelersNumber?.let { "- Number of travelers: $it" } ?: ""}
        ${tripDuration?.let { "- Duration of the trip: $it days" } ?: ""}
        ${maxBudget?.let { "- Maximum budget (USD): $it" } ?: ""}

        ### Your response must include:
        1. A day-by-day breakdown of the itinerary, listing recommended cities/landmarks to visit.
        2. For each transition between locations:
           - Specify the best transportation options (based on user preferences and feasibility).
           - Include travel duration estimates and booking recommendations.
           - Provide estimated transportation costs.
           - (If the transit is long) suggest 1–2 midway stops for rest or sightseeing.
        3. For each location visited on a given day, include:
           - Activities: 2–3 recommended things to do (e.g., landmarks, local attractions, relaxing spots, or transit-related tasks).
           - Accommodations: 1–2 suggestions with booking links.
           - Food & Dining: Local restaurant recommendations, food specialties, and estimated meal costs.
        4. For each activity, accommodation, and restaurant, provide relevant external links:
           - Determine the country of the location. If it is in Russia (e.g., Moscow, Saint Petersburg, Kazan):
             - Use Yandex Maps (https://yandex.com/maps) for all map links.
             - Use Ostrovok.ru for accommodation booking links.
           - For all other countries:
             - Use Google Maps (https://www.google.com/maps) for map links.
             - Prefer Booking.com or Airbnb for accommodations.
           - For activities, include links to ticket booking, official pages, or tour platforms when applicable.
           - For restaurants, include links to menu pages, reviews, or reservation platforms if available.
        5. Include extra tips (e.g., safety, local customs, best travel times, budget-saving tips).
        6. Present a summary of the entire itinerary, including:
           - Total estimated cost
           - Number of destinations visited
           - Estimated time spent in transit
           - Types of transport used
           - Lodging types
           - Categories covered (e.g., nature, cultural, adventure, culinary, etc.)
           
        Strictly format the response as a JSON object with the following structure:

        - \"titles\" (Map<String, String>) – A map of localized section headers (must match the keys used in the JSON body).

        - \"days\" (List<Map<String, Any>>) – A list of days in the itinerary. Each day contains:
           - \"activities\" (List<Map<String, Any>>) – A list of recommended activities for the current day. Each activity includes:
             - \"name\" (String) – The name of the activity.
             - \"description\" (String) – A full description of the activity.
             - \"time\" (String) – The scheduled time (e.g., \"10:00 - 12:00\").
             - \"links\" (List<String>) – A list of URLs or references related to the activity.
             - \"tips\" (List<String>) – Helpful tips for the activity.
             - \"activityCost\" (String) – The estimated cost in USD for the activity (e.g., \"35 USD\").
             - \"midwayStops\" (List<Map<String, Any>>) – (Optional) Suggested midway stops during long transportation segments. Each stop includes:
               - \"name\" (String) – Name of the midway stop.
               - \"description\" (String) – Description of the stop.
               - \"time\" (String) – Estimated stop duration (e.g., \"30 min\").
               - \"links\" (List<String>) – A list of URLs or references related to the stop.
               - \"tips\" (List<String>) – Helpful tips for the stop.

           - \"accommodations\" (List<Map<String, Any>>) – A list of accommodations for that day. Each item includes:
             - \"name\" (String) – The name or label for the accommodation.
             - \"link\" (String) – A link to the accommodation (e.g., \"Booking.com, Airbnb\").

        - \"summary\" (Map<String, String>) – An overview of the itinerary including:
           - \"totalCost\" (String) – The total estimated cost of the trip in USD.
           - \"visitedDestinations\" (String) – Total number of destinations visited.
           - \"transitTime\" (String) – Total estimated time spent in transit.
           - \"transportTypes\" (String) – All types of transportation used during the trip.
           - \"lodgingTypes\" (String) – Types of accommodations used (e.g., \"Hotel, Airbnb\").
           - \"categories\" (String) – Categories covered (e.g., \"nature, cultural, adventure, culinary\").
           
        ${ocrTextJsonEntry(imageUsed)}
           
           
        Example Output:
        {
          \"titles\": {
            \"day\": \"<'Day' translated>\",
            \"activities\": \"<'Activities' translated>\",
            \"description\": \"<'Description' translated>\",
            \"time\": \"<'Time' translated>\",
            \"links\": \"<'Links' translated>\",
            \"tips\": \"<'Tips' translated>\",
            \"activityCost\": \"<'Activity Cost' translated>\",
            \"midwayStops\": \"<'Midway Stops' translated>\",
            \"accommodations\": \"<'Accommodations' translated>\",
            \"link\": \"<'Link' translated>\",
            \"summary\": \"<'Summary' translated>\",
            \"totalCost\": \"<'Total Cost' translated>\",
            \"visitedDestinations\": \"<'Total Visited Destinations' translated>\",
            \"transitTime\": \"<'Total Transit Time' translated>\",
            \"transportTypes\": \"<'Transport Methods' translated>\",
            \"lodgingTypes\": \"<'Lodging Types' translated>\",
            \"categories\": \"<'Covered Categories' translated>\"
          },
          ${if (imageUsed) {"""\"ocrText\": \"A trip of 3 days with many cultural events.\","""} else ""}
          \"days\": [
            {
              \"activities\": [
                {
                  \"name\": \"Visit the Colosseum\",
                  \"description\": \"Explore the ancient Roman Colosseum with a guided tour. Learn about gladiators and Roman history.\",
                  \"time\": \"09:00 - 11:00\",
                  \"links\": [
                    \"https://example.com/colosseum-tickets\",
                    \"https://www.google.com/maps/search/Colosseum+Rome\"
                  ],
                  \"tips\": [
                    \"Book tickets in advance to skip the line.\",
                    \"Bring water and wear comfortable shoes.\"
                  ],
                  \"activityCost\": \"16 USD\"
                },
                {
                  \"name\": \"Lunch at Trastevere\",
                  \"description\": \"Enjoy authentic Roman cuisine at a cozy restaurant in Trastevere.\",
                  \"time\": \"12:30 - 13:30\",
                  \"links\": [
                    \"https://example.com/trastevere-restaurant\",
                    \"https://www.google.com/maps/search/Trastevere+Rome\"
                  ],
                  \"tips\": [
                    \"Try the Cacio e Pepe or Carbonara.\",
                    \"Lunch menus are often cheaper before 2pm.\"
                  ],
                  \"activityCost\": \"20 USD\"
                }
              ],
              \"accommodations\": [
                {
                  \"name\": \"Hotel Center Rome\",
                  \"link\": \"https://booking.com/hotel-rome-center\"
                }
              ]
            },
            {
              \"activities\": [
                {
                  \"name\": \"Train to Florence\",
                  \"description\": \"Travel from Rome to Florence by high-speed train.\",
                  \"time\": \"10:00 - 11:30\",
                  \"links\": [
                    \"https://italotreno.it\",
                    \"https://www.google.com/maps/search/Rome+to+Florence+Train\"
                  ],
                  \"tips\": [
                    \"Reserve seats in advance for cheaper rates.\",
                    \"Arrive at the station 15 minutes early.\"
                  ],
                  \"activityCost\": \"35 USD\",
                  \"midwayStops\": [
                   {
                     \"name\": \"Orvieto Station\",
                     \"description\": \"Optional quick stop to explore the historic hill town of Orvieto if using a slower regional train.\",
                     \"time\": \"30 minutes\",
                     \"links\": [
                       \"https://example.com/orvieto\",
                       \"https://www.google.com/maps/search/Orvieto+Station\"
                     ],
                     \"tips\": [
                       \"Check luggage storage options if planning a stop.\"
                     ]
                   }
                  ]
                }
              ],
              \"accommodations\": [
                {
                  \"name\": \"Florence Apartment\",
                  \"link\": \"https://airbnb.com/florence-apartment\"
                }
              ]
            }
          ],
          \"summary\": {
            \"totalCost\": \"320 USD\",
            \"visitedDestinations\": \"2\",
            \"transitTime\": \"3h 30m\",
            \"transportTypes\": \"Train, Walking\",
            \"lodgingTypes\": \"Hotel, Airbnb\",
            \"categories\": \"Cultural, Culinary\"
          }
        }
           
        $doubleQuotes
        ${jsonResponseLanguage(selectedLanguage.languageName)}
    """.trimIndent()
        }
        prompt = buildSolvingPrompt()
    }

    override fun decodeSolutionResponse(response: String): Pair<String, String> = decodeTripSolutionResponse(response)
}