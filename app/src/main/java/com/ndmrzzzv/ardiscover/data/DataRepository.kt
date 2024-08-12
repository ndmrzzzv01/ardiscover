package com.ndmrzzzv.ardiscover.data

object DataRepository {

    fun get(): List<LocationAnchor> {
        return listOf(getSplitData())
    }

    private fun getSplitData(): LocationAnchor {
        val listOfPointOfInterest = mutableListOf(
            PointOfInterest(
                "1. Camping Stobreč Split",
                Location(
                    Coordinate(latitude = 43.50410664214121f, longitude = 16.526312129899896f),
                    altitude = 7f
                )
            ),
            PointOfInterest(
                "2. Via Ferata Perunika",
                Location(
                    Coordinate(latitude = 43.5072469523574f, longitude = 16.554977474388586f),
                    altitude = 420f
                )
            ),
            PointOfInterest(
                "3. Podstrana Yacht Club",
                Location(
                    Coordinate(latitude = 43.50146104645278f, longitude = 16.532175575189505f),
                    altitude = 7f
                )
            ),
        )

        return LocationAnchor(
            "marker.jpg",
            1.20,
            Location(Coordinate(latitude = 43.5035324f, longitude = 16.5324973f), altitude = 6f),
            bearing = 215.0,
            pointsOfInterest = listOfPointOfInterest
        )
    }

}