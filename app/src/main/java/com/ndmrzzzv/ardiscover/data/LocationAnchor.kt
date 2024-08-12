package com.ndmrzzzv.ardiscover.data

data class LocationAnchor(
    val name: String,
    val physicalWidth: Double,
    val location: Location,
    val bearing: Double,
    val pointsOfInterest: List<PointOfInterest>
)
