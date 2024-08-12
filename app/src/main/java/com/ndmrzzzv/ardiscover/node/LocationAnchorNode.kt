package com.ndmrzzzv.ardiscover.node

import com.google.android.filament.Engine
import com.ndmrzzzv.ardiscover.data.LocationAnchor
import com.ndmrzzzv.ardiscover.filter.KalmanFilter
import io.github.sceneview.node.Node

class LocationAnchorNode(engine: Engine) : Node(engine) {

    private lateinit var locationAnchor: LocationAnchor
    var kalmanFilter: KalmanFilter? = null

    fun setLocationAnchor(anchor: LocationAnchor) {
        locationAnchor = anchor
    }

    fun getLocationAnchor() = locationAnchor

}