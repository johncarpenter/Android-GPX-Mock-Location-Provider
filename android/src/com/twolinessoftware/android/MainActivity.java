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

import java.io.File;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.twolinessoftware.android.framework.util.Logger;

public class MainActivity extends Activity implements GpsPlaybackListener {

	private static final int REQUEST_FILE = 1;

	private static final String LOGNAME = "SimulatedGPSProvider.MainActivity";

	private ServiceConnection connection;
	private IPlaybackService service;
	private EditText mEditText;

	private String filepath;

	private GpsPlaybackBroadcastReceiver receiver;

	private int state;

	private ProgressDialog progressDialog;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// test that mock locations are allowed so a more descriptive error
		// message can be logged
		if (Settings.Secure.getInt(getContentResolver(),
				Settings.Secure.ALLOW_MOCK_LOCATION, 0) == 0) {
			Toast.makeText(this, "MockLocations needs to be enabled",
					Toast.LENGTH_SHORT).show();
			finish();
		}

		mEditText = (EditText) findViewById(R.id.file_path);

		filepath = mEditText.getText().toString();

	}

	@Override
	protected void onStart() {
		bindStatusListener();
		connectToService();
		super.onStart();
	}

	@Override
	protected void onStop() {
		if (receiver != null)
			unregisterReceiver(receiver);

		try {
			unbindService(connection);
		} catch (Exception ie) {
		}

		super.onPause();
	}

	private void hideProgressDialog() {
		if (progressDialog != null)
			progressDialog.cancel();
	}

	private void showProgressDialog() {
		// Display progress dialog

		progressDialog = ProgressDialog.show(this,
				getString(R.string.please_wait),
				getString(R.string.loading_file), true);
		progressDialog.setCancelable(true);
		progressDialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				dialog.dismiss();
			}
		});
	}

	private void bindStatusListener() {
		receiver = new GpsPlaybackBroadcastReceiver(this);
		IntentFilter filter = new IntentFilter();
		filter.addAction(GpsPlaybackBroadcastReceiver.INTENT_BROADCAST);
		registerReceiver(receiver, filter);
	}

	private void connectToService() {
		Intent i = new Intent(getApplicationContext(), PlaybackService.class);
		connection = new PlaybackServiceConnection();
		bindService(i, connection, Context.BIND_AUTO_CREATE);
	}

	public void onClickOpenFile(View view) {
		openFile();
	}

	public void onClickStart(View view) {
		startPlaybackService();
	}

	public void onClickStop(View view) {
		stopPlaybackService();
	}

	/**
	 * Opens the file manager to select a file to open.
	 */
	public void openFile() {
		String fileName = mEditText.getText().toString();

		Intent intent = new Intent(FileManagerIntents.ACTION_PICK_FILE);

		// Construct URI from file name.
		File file = new File(fileName);
		intent.setData(Uri.fromFile(file));

		// Set fancy title and button (optional)
		intent.putExtra(FileManagerIntents.EXTRA_TITLE,
				getString(R.string.open_title));
		intent.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT,
				getString(R.string.open_button));

		try {
			startActivityForResult(intent, REQUEST_FILE);
		} catch (ActivityNotFoundException e) {
			// No compatible file manager was found.
			Toast.makeText(this, R.string.no_filemanager_installed,
					Toast.LENGTH_SHORT).show();
		}
	}

	public void startPlaybackService() {

		if (filepath == null) {
			Toast.makeText(this, "No File Loaded", Toast.LENGTH_SHORT).show();
			return;
		}

		try {
			if (service != null)
				service.startService(filepath);

		} catch (RemoteException e) {
		}

		Intent i = new Intent(getApplicationContext(), PlaybackService.class);
		startService(i);
	}

	public void stopPlaybackService() {

		try {
			if (service != null)
				service.stopService();

		} catch (RemoteException e) {
		}
	}

	private void updateUi() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Button start = (Button) findViewById(R.id.start);
				Button stop = (Button) findViewById(R.id.stop);

				switch (state) {
				case PlaybackService.RUNNING:
					start.setEnabled(false);
					stop.setEnabled(true);
					break;
				case PlaybackService.STOPPED:
					start.setEnabled(true);
					stop.setEnabled(false);
					break;
				}

			}

		});

	}

	class PlaybackServiceConnection implements ServiceConnection {

		public void onServiceConnected(ComponentName name, IBinder boundService) {
			service = IPlaybackService.Stub.asInterface(boundService);
			try {
				state = service.getState();
			} catch (RemoteException e) {
				Logger.e(LOGNAME, "Unable to access state:" + e.getMessage());
			}
			updateUi();
		}

		public void onServiceDisconnected(ComponentName name) {
			service = null;
		}

	}

	/**
	 * This is called after the file manager finished.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		case REQUEST_FILE:
			if (resultCode == RESULT_OK && data != null) {
				// obtain the filename
				Uri fileUri = data.getData();
				if (fileUri != null) {
					String filePath = fileUri.getPath();
					if (filePath != null) {
						mEditText.setText(filePath);
						this.filepath = filePath;
					}
				}
			}
			break;
		}
	}

	@Override
	public void onFileLoadStarted() {
		Logger.d(LOGNAME, "File loading started");
		showProgressDialog();
	}

	@Override
	public void onFileLoadFinished() {
		Logger.d(LOGNAME, "File loading finished");
		hideProgressDialog();
	}

	@Override
	public void onStatusChange(int newStatus) {
		state = newStatus;
		updateUi();
	}

	@Override
	public void onFileError(String message) {
		hideProgressDialog();
	}

}