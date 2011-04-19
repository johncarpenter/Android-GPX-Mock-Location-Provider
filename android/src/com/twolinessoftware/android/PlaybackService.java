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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

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

import com.twolinessoftware.android.framework.service.comms.gpx.GpxSaxParser;
import com.twolinessoftware.android.framework.service.comms.gpx.GpxSaxParserListener;
import com.twolinessoftware.android.framework.service.comms.gpx.GpxTrackPoint;
import com.twolinessoftware.android.framework.util.Logger;

public class PlaybackService extends Service implements GpxSaxParserListener {

	private NotificationManager mNM;

	private static final String LOG = PlaybackService.class.getCanonicalName();

	private static final int NOTIFICATION = 1;

	public static final long UPDATE_LOCATION_WAIT_TIME = 1000;
	
	private ArrayList<GpxTrackPoint> pointList = new ArrayList<GpxTrackPoint>(); 
	
	public static final boolean CONTINUOUS = true; 
	
	public static final int RUNNING = 0; 
	public static final int STOPPED = 1;
	
    private static final String PROVIDER_NAME = LocationManager.GPS_PROVIDER;
	
	private final IPlaybackService.Stub mBinder = new IPlaybackService.Stub() {
		
		@Override
		public void startService(String file) throws RemoteException {
			
			broadcastStateChange(RUNNING);

			loadGpxFile(file);
			
		}

		@Override
		public void stopService() throws RemoteException {
			mLocationManager.removeTestProvider(PROVIDER_NAME);
			queue.reset();
			broadcastStateChange(STOPPED);
			stopSelf(); 
		}

		@Override
		public int getState() throws RemoteException {
			return state;
		}
		
	};

	private LocationManager mLocationManager;

	private String filepath;

	private long startTimeOffset;

	private long firstGpsTime;

	private int state;

	private SendLocationWorkerQueue queue;

	@Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
	
    @Override
    public void onCreate() {
    	
    	mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		
        queue = new SendLocationWorkerQueue(); 
        queue.start(); 
       
        broadcastStateChange(STOPPED); 

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
     
    	Log.d(LOG,"Starting Playback Service");	
    
    	//disableGpsProvider(); 
		setupTestProvider();
      	
    	// We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
    	Log.d(LOG,"Stopping Playback Service");
    	
    	onGpsPlaybackStopped();
    }
    
    private void loadGpxFile(String file){
    	if(file != null){
			
    		broadcastStatus(GpsPlaybackBroadcastReceiver.Status.fileLoadStarted);
    		
    		ReadFileTask task = new ReadFileTask(file);
    		task.execute(null);
    		
    		 // Display a notification about us starting.  We put an icon in the status bar.
            showNotification();	
		}
    	
    }
    
    
    private void queueGpxPositions(String xml){
    	GpxSaxParser parser = new GpxSaxParser(this);
		parser.parse(xml);
    }
    
    private void onGpsPlaybackStopped(){
    	
    	broadcastStateChange(STOPPED); 
    	
    	// Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);

        disableGpsProvider(); 
            	
    }

    private void disableGpsProvider(){ 
    	
    	if (mLocationManager.getProvider(PROVIDER_NAME) != null) {
		    mLocationManager.removeTestProvider(PROVIDER_NAME);
		}
    }
    
    private void setupTestProvider(){
    	 mLocationManager.addTestProvider(PROVIDER_NAME, true, //requiresNetwork,
                 false, // requiresSatellite,
                 true, // requiresCell,
                 false, // hasMonetaryCost,
                 false, // supportsAltitude,
                 false, // supportsSpeed, s
                 false, // upportsBearing,
                 Criteria.POWER_MEDIUM, // powerRequirement
                 Criteria.ACCURACY_FINE); // accuracy
    }


    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = "GPX Playback Running";

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_playback_running, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, "GPX Playback Manager",text, contentIntent);

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }
    
    private void loadFile(String file){
    	
    	try{
 		   File f = new File(file);
 		 
 		   FileInputStream fileIS = new FileInputStream(f);
 		 
 		   BufferedReader buf = new BufferedReader(new InputStreamReader(fileIS));
 		 
 		   String readString = new String();
 		 
 		   StringBuffer xml = new StringBuffer(); 
 		   while((readString = buf.readLine())!= null){
 			   xml.append(readString);
 		   }
 		  
 		  Logger.d(LOG,"Finished reading in file");
 	 		
 		  queueGpxPositions(xml.toString());
 		    
 		   Logger.d(LOG,"Finished parsing in file");
 		} catch (Exception e) {
 			broadcastError("Error in the GPX file, unable to read it");
 		}
 		 		
 		
    }
   

	@Override
	public void onGpxError(String message) {
		broadcastError(message);
	}


	@Override
	public void onGpxPoint(GpxTrackPoint item) {
		
		long delay = System.currentTimeMillis() + 2000; // ms until the point should be displayed
		
		// Calculate the delay
		if(item.getTime() != null){
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			
			long gpsPointTime = 0; 
			try {
				Date gpsDate = format.parse(item.getTime());
				gpsPointTime = gpsDate.getTime(); 
			} catch (ParseException e) {
				Log.e(LOG, "Unable to parse time:"+item.getTime());
			}
			
			if(firstGpsTime == 0)
				firstGpsTime = gpsPointTime;
			
			
			if(startTimeOffset == 0)
				startTimeOffset = System.currentTimeMillis();
			
			
			delay = (gpsPointTime - firstGpsTime) + startTimeOffset;
		}
		
		
		pointList.add(item);
		
		Log.d(LOG,"Sending Point in:"+(delay-System.currentTimeMillis())+"ms");
		
		SendLocationWorker worker = new SendLocationWorker(mLocationManager,item,PROVIDER_NAME, delay);
		queue.addToQueue(worker);
		
	}

	@Override
	public void onGpxStart() {
		// Start Parsing
	}

	@Override
	public void onGpxEnd() {
		// End Parsing
	}

	private void broadcastStatus(GpsPlaybackBroadcastReceiver.Status status){
		Intent i = new Intent(GpsPlaybackBroadcastReceiver.INTENT_BROADCAST);
		i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATUS,status.toString());	
		sendBroadcast(i);	
	}
	
	private void broadcastError(String message){
		Intent i = new Intent(GpsPlaybackBroadcastReceiver.INTENT_BROADCAST);
		i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATUS,GpsPlaybackBroadcastReceiver.Status.fileError.toString());
		i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATE,state);	
		sendBroadcast(i);	
	}
	
	private void broadcastStateChange(int newState){
		state = newState; 
		Intent i = new Intent(GpsPlaybackBroadcastReceiver.INTENT_BROADCAST);
		i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATUS,GpsPlaybackBroadcastReceiver.Status.statusChange.toString());
		i.putExtra(GpsPlaybackBroadcastReceiver.INTENT_STATE,state);	
		sendBroadcast(i);	
	}
	
	private class ReadFileTask extends AsyncTask<Void,Void,Void>{

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
			loadFile(file); 
			return null; 
		}	
	}
	
}
