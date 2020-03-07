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

import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import com.twolinessoftware.android.framework.service.comms.Worker;
import com.twolinessoftware.android.framework.service.comms.gpx.GpxTrackPoint;

import java.lang.reflect.Method;

public class SendLocationWorker extends Worker {
    private static final String TAG = SendLocationWorker.class.getSimpleName();

    private GpxTrackPoint point;
    private String providerName;
    private LocationManager mLocationManager;

    private long sendTime;

    public long getSendTime() {
        return sendTime;
    }

    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    public GpxTrackPoint getPoint() {
        return this.point;
    }

    public SendLocationWorker(LocationManager mLocationManager,
                              GpxTrackPoint point, String providerName, long localSendTime) {
        super();
        this.point = point;
        this.providerName = providerName;
        this.mLocationManager = mLocationManager;
        this.sendTime = localSendTime;
    }

    @Override
    public void run() {
        sendLocation(point);
    }

    private void sendLocation(GpxTrackPoint point) {
        Log.w(TAG, "sendLocation with  : " + point.getLat() + " - " + point.getLon() + " - " + point.getTime() + " - " + point.getSpeed());
        Location loc = new Location(providerName);
        loc.setLatitude(point.getLat());
        loc.setLongitude(point.getLon());

        loc.setTime(System.currentTimeMillis());

        loc.setBearing((float) point.getHeading());
        loc.setAccuracy(1.0f);
        loc.setSpeed((float) point.getSpeed());
        loc.setAltitude(100.0);


        // bk added
        Method method;
        try {
            method = Location.class.getMethod("makeComplete", new Class[0]);
            if (method != null) {
                try {
                    method.invoke(loc, new Object[0]);
                } catch (Exception exception) {
                }
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "Sending update for " + providerName);
        mLocationManager.setTestProviderLocation(providerName, loc);
    }

}
