package com.twolinessoftware.android.framework.service.comms.gpx

data class GpxRawTrackPoint(val lat: Double, val lon: Double ) {
    var ele: String? = null
    var time: String? = null
    var fix: String? = null
    var sat: String? = null
    var heading: String? = null
    var speed: String? = null

    fun toGpxTrackPoint(): GpxTrackPoint {
        return GpxTrackPoint(lat, lon).apply {
            ele = this@GpxRawTrackPoint.ele?.toFloat()
            time = this@GpxRawTrackPoint.time
            fix = this@GpxRawTrackPoint.fix
            sat = this@GpxRawTrackPoint.sat
            heading = this@GpxRawTrackPoint.heading?.toFloat()
            speed = this@GpxRawTrackPoint.speed?.toFloat()
        }
    }
}
