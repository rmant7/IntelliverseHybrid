package com.example.shared.domain.prompt.options

import android.content.Context
import com.example.shared.R

enum class TransportationType(private val arrayIndex: Int, val type: String) {

    AIRPLANE(0, "airplane"),
    BICYCLE(1, "bicycle"),
    BOAT(2, "boat"),
    BUS(3, "bus"),
    MOTORCYCLE(4, "motorcycle"),
    OWNED_CAR(5, "Owned car"),
    RENTED_CAR(6, "rented car"),
    RIDE_SHARING(7, "ride sharing"),
    SUBWAY(8, "subway"),
    TAXI(9, "taxi"),
    TRAIN(10, "train"),
    TRAM(11, "tram"),
    WALKING(12, "walking");

    fun getString(context: Context): String {
        val transportationArray = context.resources.getStringArray(R.array.transportation_types)
        return transportationArray[arrayIndex]
    }
}
