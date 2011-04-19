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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.twolinessoftware.android.framework.util.Logger;

public class GpsPlaybackBroadcastReceiver extends BroadcastReceiver {

	public static final String INTENT_BROADCAST = "com.twolinessoftware.android.GPS_STATE";

	public static final String INTENT_STATUS = "gpsplaybackstatus";

	public static final String INTENT_STATE = "gpsplaybackstate";

	public static final String INTENT_ERROR = "gpsplaybackstateerror";

	private static final String LOGNAME = "GpsPlaybackBroadcastReceiver";

	public static enum Status {
		fileLoadStarted, fileLoadfinished, statusChange, fileError;
	}

	private GpsPlaybackListener listener;

	public GpsPlaybackBroadcastReceiver(GpsPlaybackListener _listener) {
		listener = _listener;
	}

	@Override
	public void onReceive(Context context, Intent intent) {

		String status = intent.getStringExtra(INTENT_STATUS);
		int state = intent.getIntExtra(INTENT_STATE, -1);
		String error = intent.getStringExtra(INTENT_ERROR);

		Logger.d(LOGNAME, "Sending Status Update:" + status);

		if (listener != null) {
			switch (Status.valueOf(status)) {
			case fileLoadStarted:
				listener.onFileLoadStarted();
				break;
			case fileLoadfinished:
				listener.onFileLoadFinished();
				break;
			case statusChange:
				listener.onStatusChange(state);
				break;
			case fileError:
				listener.onFileError(error);
				break;
			default:
				Logger.e(LOGNAME, "Unknown status in receiver:" + status);
			}
		}

	}

}
