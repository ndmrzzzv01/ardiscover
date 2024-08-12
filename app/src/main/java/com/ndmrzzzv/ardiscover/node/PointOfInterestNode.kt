package com.ndmrzzzv.ardiscover.node

import com.google.android.filament.Engine
import com.ndmrzzzv.ardiscover.data.PointOfInterest
import io.github.sceneview.node.Node

class PointOfInterestNode(engine: Engine) : Node(engine) {

    private lateinit var pointOfInterest: PointOfInterest

    fun setPoint(pointOfInterest: PointOfInterest) {
        this.pointOfInterest = pointOfInterest
    }

    fun getPoint() = pointOfInterest
}