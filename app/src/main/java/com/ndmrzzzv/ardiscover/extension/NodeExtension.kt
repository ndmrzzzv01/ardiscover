package com.ndmrzzzv.ardiscover.extension

import com.ndmrzzzv.ardiscover.data.Coordinate
import dev.romainguy.kotlin.math.Float2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val earthRadius = 6371e3

fun zeroPoint() = Float2(0f, 0f)

fun Float.degreesToRadians() = this * Math.PI / 180

fun Float2.bearing(anotherPoint: Float2): Double {
    return atan2(
        (anotherPoint.x - this.x).toDouble(),
        (anotherPoint.y - this.y).toDouble()
    )
}

fun Float2.distance(anotherPoint: Float2): Double {
    val x = (this.x - anotherPoint.x).toDouble()
    val y = (this.y - anotherPoint.y).toDouble()
    return sqrt((x * x) + (y * y))
}

fun Float2.destination(bearing: Double, distance: Double): Float2 {
    val x = (distance * sin(bearing)).toFloat()
    val y = (distance * cos(bearing)).toFloat()

    return Float2(x + this.x, y + this.y)
}

fun Coordinate.greatCircleDistance(anotherCoordinate: Coordinate): Double {
    val R = earthRadius
    val f1 = this.latitude.degreesToRadians()
    val f2 = anotherCoordinate.latitude.degreesToRadians()

    val df =
        (anotherCoordinate.latitude - this.latitude).degreesToRadians()
    val dl =
        (anotherCoordinate.longitude - this.longitude).degreesToRadians()

    val a = sin(df / 2) * sin(df / 2) + cos(f1) * cos(f2) * sin(dl / 2) * sin(dl / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return R * c
}

fun Coordinate.initialBearing(anotherCoordinate: Coordinate): Float {
    val a1 = this.latitude.degreesToRadians()
    val a2 = anotherCoordinate.latitude.degreesToRadians()

    val b1 = this.longitude.degreesToRadians()
    val b2 = anotherCoordinate.longitude.degreesToRadians()

    val y = sin(b2 - b1) * cos(a2)
    val x = cos(a1) * sin(a2) - sin(a1) * cos(a2) * cos(b2 - b1)

    return atan2(y, x).toFloat()
}

fun Coordinate.relativePoint(anotherCoordinate: Coordinate): Float2 {
    val distance = this.greatCircleDistance(anotherCoordinate)
    val bearing = this.initialBearing(anotherCoordinate)

    val x = (distance * sin(bearing)).toFloat()
    val y = (distance * cos(bearing)).toFloat()

    return Float2(x, y)
}