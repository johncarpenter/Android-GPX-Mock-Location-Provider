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
package com.twolinessoftware.android;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.twolinessoftware.android.framework.service.comms.gpx.GpxSaxParser;
import com.twolinessoftware.android.framework.service.comms.gpx.GpxSaxParserListener;
import com.twolinessoftware.android.framework.service.comms.gpx.GpxTrackPoint;
import com.twolinessoftware.android.framework.util.Logger;
import com.vividsolutions.jts.geom.Coordinate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class PlaybackService extends Service implements GpxSaxParserListener {
    private static final String TAG = PlaybackService.class.getSimpleName();

    private NotificationManager mNM;
    private static final String NOTIFICATION_CHANNEL_ID_DEFAULT = "default";
    private static final int NOTIFICATION = 1;

    private ArrayList<GpxTrackPoint> pointList = new ArrayList<GpxTrackPoint>();
    public static final boolean CONTINUOUS = true;
    public static final int RUNNING = 0;
    public static final int STOPPED = 1;
    public static final int PAUSED = 2;
    public static final int RESUME = 3;
    private static final String PROVIDER_NAME = LocationManager.GPS_PROVIDER;

    private GpxTrackPoint lastPoint;
    long delayTimeOnReplay = 0;

    private final IPlaybackService.Stub mBinder = new IPlaybackService.Stub() {


        @Override
        public void startService(String file) throws RemoteException {
            broadcastStateChange(RUNNING);
            loadGpxFile(file);
            setupTestProvider();
        }

        @Override
        public void stopService() throws RemoteException {
            //mLocationManager.removeTestProvider(PROVIDER_NAME);
            queuePause.reset();
            queue.reset();
            broadcastStateChange(STOPPED);
            cancelExistingTaskIfNecessary();
            onGpsPlaybackStopped();
            stopSelf();
        }

        @Override
        public int getState() throws RemoteException {
            return state;
        }

        @Override
        public void pause() {
            Log.e(TAG, "Pausing Playback Service");
            broadcastStateChange(PAUSED);
            queue.pause();

            GpxTrackPoint currentPointWorker = queue.getCurrentPointWorker();
            if (currentPointWorker != null) {
                Log.d(TAG, "Sending Point at pause  !!!!!!! " + currentPointWorker.getLat() + " - " + currentPointWorker.getLon() + " speed : " + currentPointWorker.getSpeed());
                for (int i = 0; i < QUEUE_PAUSE_SIZE; i++) {
                    SendLocationWorker worker = new SendLocationWorker(mLocationManager, currentPointWorker, PROVIDER_NAME, System.currentTimeMillis());
                    queuePause.addToQueue(worker);
                    if (queuePause.getQueueSize() > 0 && !queuePause.isRunning()) {
                        Log.e(TAG, " Start pause");
                        queuePause.start(delayTimeOnReplay);
                    }
                }
            }
        }

        @Override
        public void resume() {
            Log.e(TAG, "Resuming Playback Service");
            broadcastStateChange(RESUME);
            queuePause.reset();
            queue.resume();
        }

        @Override
        public void updateDelayTime(long timeInMilliseconds){
            if(state == PAUSED) {
                Log.e(TAG, "Updating Delay Time Playback Service");
                queue.updateDelayTime(timeInMilliseconds);
            }
        }
    };

    private LocationManager mLocationManager;
    private long startTimeOffset;
    private long firstGpsTime;
    private int state;
    private SendLocationWorkerQueue queue, queuePause;
    private final int QUEUE_PAUSE_SIZE = 100;
    private boolean processing;
    private ReadFileTask task;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        queue = new SendLocationWorkerQueue();
        queuePause = new SendLocationWorkerQueue();
        broadcastStateChange(STOPPED);
        //setupTestProvider();
        processing = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting Playback Service");

        String timeFromIntent = null;
        try {
            timeFromIntent = intent.getStringExtra("delayTimeOnReplay");
        } catch (NullPointerException npe) {
            // suppress npe if delay time not available.
            npe.printStackTrace();
        }

        if (timeFromIntent != null && !"".equalsIgnoreCase(timeFromIntent)) {
            delayTimeOnReplay = Long.parseLong(timeFromIntent);
            queue.start(delayTimeOnReplay);
        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Stopping Playback Service");
    }

    private void cancelExistingTaskIfNecessary() {
        if (task != null) {
            try {
                task.cancel(true);
            } catch (Exception e) {
                Log.e(TAG, "Unable to cancel playback task. May already be stopped");
            }
        }
    }

    private void loadGpxFile(String file) {
        if (file != null) {
            broadcastStatus(GpsPlaybackBroadcastReceiver.Status.fileLoadStarted);
            cancelExistingTaskIfNecessary();
            task = new ReadFileTask(file);
            task.execute(null, null);
            // Display a notification about us starting.  We put an icon in the status bar.
            showNotification();
        }

    }


    private void queueGpxPositions(String xml) {
        GpxSaxParser parser = new GpxSaxParser(this);
        parser.parse(xml);
    }

    private void onGpsPlaybackStopped() {
        broadcastStateChange(STOPPED);
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);
        disableGpsProvider();
    }

    private void disableGpsProvider() {
        if (mLocationManager.getProvider(PROVIDER_NAME) != null) {
            mLocationManager.setTestProviderEnabled(PROVIDER_NAME, false);
            mLocationManager.clearTestProviderEnabled(PROVIDER_NAME);
            mLocationManager.clearTestProviderLocation(PROVIDER_NAME);
            mLocationManager.removeTestProvider(PROVIDER_NAME);
        }
    }

    private void setupTestProvider() {
        mLocationManager.addTestProvider(PROVIDER_NAME, false, //requiresNetwork,
                false, // requiresSatellite,
                false, // requiresCell,
                false, // hasMonetaryCost,
                false, // supportsAltitude,
                false, // supportsSpeed, s
                false, // upportsBearing,
                Criteria.POWER_LOW, // powerRequirement
                Criteria.ACCURACY_FINE); // accuracy

        mLocationManager.setTestProviderEnabled("gps", true);
    }


    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = "GPX Playback Running";

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        final Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_DEFAULT)
                .setSmallIcon(R.drawable.ic_playback_running)
                .setContentText(text)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(contentIntent)
                .setContentTitle("GPX Playback Manager")
                .build();

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }

    private String loadFile(String file) {
        try {
            File f = new File(file);
            FileInputStream fileIS = new FileInputStream(f);
            BufferedReader buf = new BufferedReader(new InputStreamReader(fileIS));
            String readString = new String();
            StringBuffer xml = new StringBuffer();
            while ((readString = buf.readLine()) != null) {
                xml.append(readString);
            }
            Logger.d(TAG, "Finished reading in file");
            return xml.toString();
        } catch (Exception e) {
            broadcastError("Error in the GPX file, unable to read it");
        }
        return null;
    }


    @Override
    public void onGpxError(String message) {
        broadcastError(message);
    }


    @Override
    public void onGpxPoint(GpxTrackPoint item) {
        long delay = System.currentTimeMillis() + 2000; // ms until the point should be displayed

        long gpsPointTime = 0;

        // Calculate the delay
        if (item.getTime() != null) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

            try {
                Date gpsDate = format.parse(item.getTime());
                gpsPointTime = gpsDate.getTime();
            } catch (ParseException e) {
                Log.e(TAG, "Unable to parse time:" + item.getTime());
            }

            if (firstGpsTime == 0)
                firstGpsTime = gpsPointTime;

            if (startTimeOffset == 0)
                startTimeOffset = System.currentTimeMillis();

            delay = (gpsPointTime - firstGpsTime) + startTimeOffset;
        }

        if (lastPoint != null) {
            item.setHeading(calculateHeadingFromPreviousPoint(lastPoint, item));
            item.setSpeed(calculateSpeedFromPreviousPoint(lastPoint, item));
        } else {
            item.setHeading(0.0);
            item.setSpeed(15.0);
        }

        lastPoint = item;

        pointList.add(item);
        if (state == RUNNING) {
            if (delay > 0) {
                Log.d(TAG, "Sending Point in:" + (delay - System.currentTimeMillis()) + "ms");

                SendLocationWorker worker = new SendLocationWorker(mLocationManager, item, PROVIDER_NAME, delay);
                queue.addToQueue(worker);
            } else {
                Log.e(TAG, "Invalid Time at Point:" + gpsPointTime + " delay from current time:" + delay);
            }
        }

    }

    private double calculateHeadingFromPreviousPoint(GpxTrackPoint currentPoint, GpxTrackPoint lastPoint) {

        double angleBetweenPoints = Math.atan2((lastPoint.getLon() - currentPoint.getLon()), (lastPoint.getLat() - currentPoint.getLat()));
        return Math.toDegrees(angleBetweenPoints);
    }

    private double calculateSpeedFromPreviousPoint(GpxTrackPoint currentPoint, GpxTrackPoint lastPoint) {

        Coordinate startCoordinate = new Coordinate(lastPoint.getLon(), lastPoint.getLat());
        Coordinate endCoordinate = new Coordinate(currentPoint.getLon(), currentPoint.getLat());
        double distance = startCoordinate.distance(endCoordinate) * 100000;
        return distance;

    }

    @Override
    public void onGpxStart() {
        // Start Parsing
    }

    @Override
    public void onGpxEnd() {
        // End Parsing
    }

    private void broadcastStatus(GpsPlaybackBroadcastReceiver.Status status) {
        Intent i = new Intent(GpsPlaybackBroadcastReceiver.INTENT_BROADCAST);
        i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATUS, status.toString());
        sendBroadcast(i);
    }

    private void broadcastError(String message) {
        Intent i = new Intent(GpsPlaybackBroadcastReceiver.INTENT_BROADCAST);
        i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATUS, GpsPlaybackBroadcastReceiver.Status.fileError.toString());
        i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATE, state);
        sendBroadcast(i);
    }

    private void broadcastStateChange(int newState) {
        state = newState;
        Intent i = new Intent(GpsPlaybackBroadcastReceiver.INTENT_BROADCAST);
        i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATUS, GpsPlaybackBroadcastReceiver.Status.statusChange.toString());
        i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATE, state);
        sendBroadcast(i);
    }

    private class ReadFileTask extends AsyncTask<Void, Integer, Void> {

        private String file;

        public ReadFileTask(String file) {
            super();
            this.file = file;
        }

        @Override
        protected void onPostExecute(Void result) {
            broadcastStatus(GpsPlaybackBroadcastReceiver.Status.fileLoadfinished);
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            // Reset the existing values
            firstGpsTime = 0;
            startTimeOffset = 0;

            String xml = loadFile(file);
            publishProgress(1);
            queueGpxPositions(xml);
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            switch (progress[0]) {
                case 1:
                    broadcastStatus(GpsPlaybackBroadcastReceiver.Status.fileLoadfinished);
                    break;
            }
        }
    }
}
