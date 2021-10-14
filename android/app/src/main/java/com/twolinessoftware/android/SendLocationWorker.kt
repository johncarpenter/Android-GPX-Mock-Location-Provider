/*
 * Copyright (c) 2011 2linessoftware.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twolinessoftware.android

import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.twolinessoftware.android.framework.service.comms.Worker
import com.twolinessoftware.android.framework.service.comms.gpx.GpxTrackPoint
import java.lang.reflect.Method

class SendLocationWorker(private val mLocationManager: LocationManager?,
                         private val point: GpxTrackPoint, private val providerName: String, var sendTime: Long) : Worker() {
    override fun run() {
        sendLocation(point)
    }

    private fun sendLocation(point: GpxTrackPoint) {
        val loc = Location(providerName)
        loc.latitude = point.lat
        loc.longitude = point.lon
        loc.time = System.currentTimeMillis()
        point.heading?.let { loc.bearing = it }
        loc.accuracy = 1.0f
        point.speed?.let { loc.speed = it }
        point.ele?.let {
            loc.altitude = it.toDouble()
        } ?: 100f

        // bk added
        val method: Method?
        try {
            method = Location::class.java.getMethod("makeComplete", *arrayOfNulls(0))
            if (method != null) {
                try {
                    method.invoke(loc, *arrayOfNulls(0))
                } catch (exception: Exception) {
                }
            }
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        }
        Log.d("SendLocation", "Sending update for $providerName")
        mLocationManager?.setTestProviderLocation(providerName, loc)
    }
}