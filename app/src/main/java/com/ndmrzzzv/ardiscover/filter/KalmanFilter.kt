package com.ndmrzzzv.ardiscover.filter

class KalmanFilter(
    private var stateEstimate: Float,
    private var estimateUncertainty: Float
) {

    fun update(measurement: Float, measurementUncertainty: Float) {
        val kalmanGain = estimateUncertainty / (estimateUncertainty + measurementUncertainty)
        stateEstimate += kalmanGain * (measurement - stateEstimate)
        estimateUncertainty *= (1 - kalmanGain)
    }

    fun getEstimate() = stateEstimate

}