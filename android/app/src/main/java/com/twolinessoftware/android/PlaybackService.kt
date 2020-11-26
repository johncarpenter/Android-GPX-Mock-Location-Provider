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

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.LocationManager
import android.os.AsyncTask
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.Toast
import com.twolinessoftware.android.MockUtils.Companion.canMockLocation
import com.twolinessoftware.android.framework.service.comms.gpx.GpxSaxParser
import com.twolinessoftware.android.framework.service.comms.gpx.GpxSaxParserListener
import com.twolinessoftware.android.framework.service.comms.gpx.GpxTrackPoint
import com.twolinessoftware.android.framework.util.Logger
import com.vividsolutions.jts.geom.Coordinate
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan2

class PlaybackService : Service(), GpxSaxParserListener {
    private var notificationManager: NotificationManager? = null
    private val pointList = ArrayList<GpxTrackPoint>()
    private var lastPoint: GpxTrackPoint? = null
    private val mBinder: IPlaybackService.Stub = object : IPlaybackService.Stub() {
        @Throws(RemoteException::class)
        override fun startService(file: String) {

        }

        @Throws(RemoteException::class)
        override fun stopService() {
            mLocationManager?.removeTestProvider(PROVIDER_NAME)
            queue?.reset()
            broadcastStateChange(STOPPED)
            cancelExistingTaskIfNecessary()
            onGpsPlaybackStopped()
            stopSelf()
        }

        @Throws(RemoteException::class)
        override fun getState(): Int {
            return this@PlaybackService.state
        }
    }
    private var mLocationManager: LocationManager? = null
    private var startTimeOffset: Long = 0
    private var firstGpsTime: Long = 0
    private var state = 0
    private var queue: SendLocationWorkerQueue? = null
    private var processing = false
    private var task: ReadFileTask? = null
    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onCreate() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mLocationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        queue = SendLocationWorkerQueue()
        broadcastStateChange(STOPPED)
        if (Build.VERSION.SDK_INT >= 26) {
            val CHANNEL_ID = "my_channel_01"
            val channel = NotificationChannel(CHANNEL_ID,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("").build()
            startForeground(1, notification)
        }
        setupTestProvider()
        processing = false
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(LOG, "Starting Playback Service")

        val staticLocation = intent.getStringExtra(INTENT_STATIC_LOCATION)
        if (staticLocation != null) {
            var location = STATIC_LOCATIONS[staticLocation]
            if (location == null) {
                location = STATIC_LOCATIONS["Berlin"]
            }
            location?.let {
                var gpxPoint = GpxTrackPoint()
                gpxPoint.ele = 0f
                gpxPoint.heading = 0.0
                gpxPoint.lat = location["latitude"] ?: 52.52
                gpxPoint.lon = location["longitude"] ?: 13.13
                queue?.addToQueue(SendLocationWorker(mLocationManager, gpxPoint, PROVIDER_NAME,
                        0))
                queue?.start(0)
            }
            return START_STICKY
        }

        var timeFromIntent: String? = null
        try {
            timeFromIntent = intent.getStringExtra(INTENT_DELAY_TIME_ON_REPLAY)
        } catch (npe: NullPointerException) {
            // suppress npe if delay time not available.
        }

        val fileName = intent.getStringExtra(INTENT_FILENAME)
        loadGpxFile(fileName)

        if (timeFromIntent != null && !"".equals(timeFromIntent, ignoreCase = true)) {
            val delayTimeOnReplay = java.lang.Long.valueOf(timeFromIntent)
            queue?.start(delayTimeOnReplay)
        } else {
            queue?.start(0)
        }

        broadcastStateChange(RUNNING)
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(LOG, "Stopping Playback Service")
    }

    private fun cancelExistingTaskIfNecessary() {
        try {
            task?.cancel(true)
        } catch (e: Exception) {
            Log.e(LOG, "Unable to cancel playback task. May already be stopped")
        }
    }

    private fun loadGpxFile(file: String?) {
        if (file != null) {
            broadcastStatus(GpsPlaybackBroadcastReceiver.Status.fileLoadStarted)
            cancelExistingTaskIfNecessary()
            task = ReadFileTask(file)
            task?.execute(null, null)

            // Display a notification about us starting.  We put an icon in the status bar.
            showNotification()
        }
    }

    private fun queueGpxPositions(xml: String) {
        val parser = GpxSaxParser(this)
        parser.parse(xml)
    }

    private fun onGpsPlaybackStopped() {
        broadcastStateChange(STOPPED)

        // Cancel the persistent notification.
        notificationManager?.cancel(NOTIFICATION)
        disableGpsProvider()
    }

    private fun disableGpsProvider() {
        mLocationManager?.let {
            if (it.getProvider(PROVIDER_NAME) != null) {
                try {
                    it.setTestProviderEnabled(PROVIDER_NAME, false)
                    it.clearTestProviderEnabled(PROVIDER_NAME)
                    it.clearTestProviderLocation(PROVIDER_NAME)
                    it.removeTestProvider(PROVIDER_NAME)
                } catch (e: IllegalArgumentException) {
                    Log.d("Main", "Error removing mock provider, probably a real one exist " +
                            "with the same name.")
                } catch (securityError: SecurityException) {
                    Toast.makeText(this, "Please grant permission to use mock location first. " +
                            "This can be done in Development Settings or via adb.", Toast.LENGTH_LONG).show()
                }
            }
        }

    }

    private fun setupTestProvider() {
        disableGpsProvider()
        if (canMockLocation(this)) {
            mLocationManager?.let {
                it.addTestProvider(PROVIDER_NAME, false,  //requiresNetwork,
                        false,  // requiresSatellite,
                        false,  // requiresCell,
                        false,  // hasMonetaryCost,
                        false,  // supportsAltitude,
                        false,  // supportsSpeed, s
                        false,  // supportsBearing,
                        Criteria.POWER_LOW,  // powerRequirement
                        Criteria.ACCURACY_FINE) // accuracy
                it.setTestProviderEnabled(PROVIDER_NAME, true)
            }
        }
    }

    /**
     * Show a notification while this service is running.
     */
    private fun showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        val text: CharSequence = "GPX Playback Running"

        // The PendingIntent to launch our activity if the user selects this notification
        val contentIntent = PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), 0)
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_DEFAULT)
                .setSmallIcon(R.drawable.ic_playback_running)
                .setContentText(text)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(contentIntent)
                .setContentTitle("GPX Playback Manager")
                .build()

        // Send the notification.
        notificationManager?.notify(NOTIFICATION, notification)
    }

    private fun loadFile(file: String): String? {
        try {
            val f = File(file)
            val fileIS = FileInputStream(f)
            val buf = BufferedReader(InputStreamReader(fileIS))
            var readString: String? = String()
            val xml = StringBuffer()
            while (buf.readLine().also { readString = it } != null) {
                xml.append(readString)
            }
            Logger.d(LOG, "Finished reading in file")
            return xml.toString()
        } catch (e: Exception) {
            broadcastError("Error in the GPX file, unable to read it")
        }
        return null
    }

    override fun onGpxError(message: String?) {
        broadcastError(message)
    }

    override fun onGpxPoint(item: GpxTrackPoint) {
        var delay = System.currentTimeMillis() + 2000 // ms until the point should be displayed
        var gpsPointTime: Long = 0
        var parsed = false

        // Calculate the delay
        if (item.time != null) {
            for (element in DATE_FORMATTERS) {
                try {
                    val gpsDate = element.parse(item.time)
                    gpsPointTime = gpsDate.time
                    parsed = true
                    break
                } catch (e: ParseException) {
                }
            }
            if (!parsed) {
                Log.e(LOG, "Unable to parse time:" + item.time)
                return
            }

            if (firstGpsTime == 0L) firstGpsTime = gpsPointTime
            if (startTimeOffset == 0L) startTimeOffset = System.currentTimeMillis()
            delay = gpsPointTime - firstGpsTime + startTimeOffset
        }
        lastPoint?.also {
            item.heading = calculateHeadingFromPreviousPoint(it, item)
            item.speed = calculateSpeedFromPreviousPoint(it, item)
        } ?: run {
            item.heading = 0.0
            item.speed = 15.0
        }
        lastPoint = item
        pointList.add(item)
        if (state == RUNNING) {
            if (delay > 0) {
                Log.d(LOG, "Sending Point in:" + (delay - System.currentTimeMillis()) + "ms")
                val worker = SendLocationWorker(mLocationManager, item, PROVIDER_NAME, delay)
                queue?.addToQueue(worker)
            } else {
                Log.e(LOG, "Invalid Time at Point:$gpsPointTime delay from current time:$delay")
            }
        }
    }

    override fun onGpxStart() {
        Log.d(LOG, "Parsing GPX started.")
    }

    override fun onGpxEnd() {
        Log.d(LOG, "Parsing GPX completed.")
    }

    private fun calculateHeadingFromPreviousPoint(currentPoint: GpxTrackPoint, lastPoint: GpxTrackPoint): Double {
        val angleBetweenPoints = atan2(lastPoint.lon - currentPoint.lon, lastPoint.lat - currentPoint.lat)
        return Math.toDegrees(angleBetweenPoints)
    }

    private fun calculateSpeedFromPreviousPoint(currentPoint: GpxTrackPoint, lastPoint: GpxTrackPoint): Double {
        val startCoordinate = Coordinate(lastPoint.lon, lastPoint.lat)
        val endCoordinate = Coordinate(currentPoint.lon, currentPoint.lat)
        return startCoordinate.distance(endCoordinate) * 100000
    }

    private fun broadcastStatus(status: GpsPlaybackBroadcastReceiver.Status) {
        val i = Intent(GpsPlaybackBroadcastReceiver.INTENT_BROADCAST)
        i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATUS, status.toString())
        sendBroadcast(i)
    }

    private fun broadcastError(message: String?) {
        val i = Intent(GpsPlaybackBroadcastReceiver.INTENT_BROADCAST)
        i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATUS, GpsPlaybackBroadcastReceiver.Status.fileError.toString())
        i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATE, state)
        sendBroadcast(i)
    }

    private fun broadcastStateChange(newState: Int) {
        state = newState
        val i = Intent(GpsPlaybackBroadcastReceiver.INTENT_BROADCAST)
        i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATUS, GpsPlaybackBroadcastReceiver.Status.statusChange.toString())
        i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATE, state)
        sendBroadcast(i)
    }

    private inner class ReadFileTask(private val file: String) : AsyncTask<Void?, Int?, Void?>() {
        override fun onPostExecute(result: Void?) {
            broadcastStatus(GpsPlaybackBroadcastReceiver.Status.fileLoadfinished)
        }

        override fun doInBackground(vararg arg0: Void?): Void? {
            // Reset the existing values
            firstGpsTime = 0
            startTimeOffset = 0
            loadFile(file)?.let {
                queueGpxPositions(it)
            }
            publishProgress(1)
            return null
        }

        override fun onProgressUpdate(vararg progress: Int?) {
            when (progress[0]) {
                1 -> broadcastStatus(GpsPlaybackBroadcastReceiver.Status.fileLoadfinished)
            }
        }
    }

    companion object {
        const val RUNNING = 0
        const val STOPPED = 1
        const val INTENT_DELAY_TIME_ON_REPLAY = "delayTimeOnReplay"
        const val INTENT_FILENAME = "filename"
        const val INTENT_STATIC_LOCATION = "location"

        private val STATIC_LOCATIONS = hashMapOf(
                "Berlin" to mapOf("latitude" to 52.5310158, "longitude" to 13.3842838),
                "San Francisco" to mapOf("latitude" to 37.7873055, "longitude" to -122.4092979)
        )

        private val DATE_FORMATTERS = arrayOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        )
        private const val NOTIFICATION_CHANNEL_ID_DEFAULT = "default"
        private val LOG = PlaybackService::class.java.simpleName
        private const val NOTIFICATION = 1
        private const val PROVIDER_NAME = LocationManager.GPS_PROVIDER
    }
}