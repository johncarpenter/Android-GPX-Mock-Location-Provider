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

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.twolinessoftware.android.framework.util.Logger;
import com.twolinessoftware.android.util.LocationUtil;
import com.twolinessoftware.android.util.PermissionUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity implements GpsPlaybackListener, View.OnClickListener {

    private String[] APP_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};

    private static final int REQUEST_FILE = 1;
    private static final int REQUEST_MOCK_LOCATION = 2;
    private static final int REQUEST_PERMISSION_APP_SETTING = 3;
    private static final int REQUEST_APP_PERMISSION = 4;

    private static final String TAG = MainActivity.class.getSimpleName();

    private ServiceConnection connection;
    private IPlaybackService service;
    private EditText mEditText;
    private ImageView mImageViewFileManager;
    private EditText mEditTextDelay;
    private RadioGroup mRadioGroupDelay;
    private RadioButton mRadioButtonChecked;
    private Button mButtonStart;
    private Button mButtonStop;

    private String filepath;
    private String delayTimeOnReplay = "";
    private GpsPlaybackBroadcastReceiver receiver;
    private int state;
    private ProgressDialog progressDialog;

    private static final String APP_DATA_CACHE_FILENAME = "gpx_app_data_cache";
    private static final String DEFAULT_PATH_TO_GPX_FILE = "/";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mEditText = findViewById(R.id.file_path);
        mImageViewFileManager = findViewById(R.id.file_manager);
        mEditTextDelay = findViewById(R.id.editTextDelay);
        mRadioGroupDelay = findViewById(R.id.radioGroupDelay);
        mButtonStart = findViewById(R.id.start);
        mButtonStop = findViewById(R.id.stop);

        initHandler();
    }

    private void initHandler() {
        mImageViewFileManager.setOnClickListener(this);
        mButtonStart.setOnClickListener(this);
        mButtonStop.setOnClickListener(this);

        mEditTextDelay.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                delayTimeOnReplay = mEditTextDelay.getText().toString();
            }
        });

        mEditText.setFocusable(false);
        mEditText.setOnFocusChangeListener((view, b) -> {
            if (b) {
                hideKeyboard(view, MainActivity.this);
            }
        });

        mRadioGroupDelay.setOnCheckedChangeListener((radioGroup, i) -> {
            int selectId = radioGroup.getCheckedRadioButtonId();
            mRadioButtonChecked = findViewById(selectId);
            delayTimeOnReplay = mRadioButtonChecked.getText().toString();
            mEditTextDelay.setText(delayTimeOnReplay);
            if(state == PlaybackService.PAUSED) {
                updateDelayTimePlayService(Long.parseLong(delayTimeOnReplay));
            }
        });

        String fileName = getGpxFilePath();
        if(fileName != null) {
            filepath = fileName;
            mEditText.setText(filepath);
        }

        mButtonStart.setEnabled(filepath != null);

        if (!hasPermissions(APP_PERMISSIONS)) {
            requestAppPermission();
        }
    }

    @Override
    protected void onStart() {
        if (LocationUtil.isMockLocationEnabled(this)) {
            bindStatusListener();
            connectToService();
        } else {
            showMockLocationSettings();
        }
        super.onStart();
    }

    @Override
    protected void onStop() {
        if (LocationUtil.isMockLocationEnabled(this)) {
            if (receiver != null)
                unregisterReceiver(receiver);
            try {
                unbindService(connection);
            } catch (Exception ie) {
            }
        }

        super.onStop();

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
        progressDialog.setOnCancelListener(dialog -> dialog.dismiss());
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


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.file_manager:
                openFile();
                break;
            case R.id.start:
                switch (state) {
                    case PlaybackService.STOPPED:
                        startPlaybackService();
                        break;
                    case PlaybackService.RUNNING:
                    case PlaybackService.RESUME:
                        pausePlaybackService();
                        break;
                    case PlaybackService.PAUSED:
                        resumePlaybackService();
                        break;
                }
                break;
            case R.id.stop:
                stopPlaybackService();
                break;
        }
    }

    /**
     * Opens the file manager to select a file to open.
     */
    public void openFile() {
        String fileName = getGpxFilePath();
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
            Toast.makeText(this, R.string.no_file_manager_installed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /*
     * "Start" button clicked
     */
    public void startPlaybackService() {
        if (filepath == null) {
            Toast.makeText(this, "No File Loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        if (delayTimeOnReplay == null) {
            Toast.makeText(this, "No delay time specified", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (service != null) {
                service.startService(filepath);
            }

        } catch (RemoteException e) {
            e.printStackTrace();
        }

        Intent i = new Intent(getApplicationContext(), PlaybackService.class);
        i.putExtra("delayTimeOnReplay", delayTimeOnReplay);
        startService(i);
    }

    public void stopPlaybackService() {

        try {
            if (service != null) {
                saveGpxFilePath(filepath);
                mEditText.setText(filepath);
                service.stopService();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void pausePlaybackService() {
        try {
            if (service != null) {
                service.pause();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void resumePlaybackService() {
        try {
            if (service != null) {
                service.resume();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void updateDelayTimePlayService(long timeInMilliseconds) {
        try {
            if(service !=null) {
                service.updateDelayTime(timeInMilliseconds);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void updateUi() {
        runOnUiThread(() -> {
            switch (state) {
                case PlaybackService.RUNNING:
                case PlaybackService.RESUME:
                    if(state == PlaybackService.RUNNING) {
                        mEditText.setEnabled(false);
                        mImageViewFileManager.setEnabled(false);
                    }
                    mEditTextDelay.setEnabled(false);
                    enableRadioGroupDelay(false);
                    mButtonStart.setText(getString(R.string.pause_playback));
                    mButtonStart.setEnabled(true);
                    mButtonStop.setEnabled(true);
                    break;
                case PlaybackService.STOPPED:
                    mEditText.setEnabled(true);
                    mImageViewFileManager.setEnabled(true);
                    mEditTextDelay.setEnabled(true);
                    enableRadioGroupDelay(true);
                    mButtonStart.setText(getString(R.string.start_playback));
                    mButtonStart.setEnabled(true);
                    mButtonStop.setEnabled(false);
                    break;
                case PlaybackService.PAUSED:
                    mEditTextDelay.setEnabled(true);
                    enableRadioGroupDelay(true);
                    mButtonStart.setText(getString(R.string.resume_playback));
                    mButtonStart.setEnabled(true);
                    mButtonStop.setEnabled(true);
                    break;
            }
        });

    }

    private void enableRadioGroupDelay(boolean isEnable) {
        for(int i = 0; i < mRadioGroupDelay.getChildCount(); i ++) {
            mRadioGroupDelay.getChildAt(i).setEnabled(isEnable);
        }
    }

    class PlaybackServiceConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName name, IBinder boundService) {
            service = IPlaybackService.Stub.asInterface(boundService);
            try {
                state = service.getState();
            } catch (RemoteException e) {
                Logger.e(TAG, "Unable to access state:" + e.getMessage());
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
                            hideKeyboard(mEditText, this);
                            this.filepath = filePath;
                            mButtonStart.setEnabled(true);
                        }
                    }
                }
                break;
            case REQUEST_MOCK_LOCATION:
                /*if(resultCode == RESULT_OK) */
                if (!LocationUtil.isMockLocationEnabled(this)) {
                    showMockLocationSettings();
                }
                break;
            case REQUEST_PERMISSION_APP_SETTING:
                if (!hasPermissions(APP_PERMISSIONS)) {
                    requestAppPermission();
                }
                break;
        }
    }

    @Override
    public void onFileLoadStarted() {
        Logger.d(TAG, "File loading started");
        showProgressDialog();
    }

    @Override
    public void onFileLoadFinished() {
        Logger.d(TAG, "File loading finished");
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

    /**
     * Saves filepath to private application data saved on disk.
     *
     * @param filepath
     */
    public void saveGpxFilePath(String filepath) {

        try {
            FileOutputStream fos = openFileOutput(APP_DATA_CACHE_FILENAME, Context.MODE_PRIVATE);
            if (filepath != null) {
                fos.write(filepath.getBytes());
            } else {
                fos.write(DEFAULT_PATH_TO_GPX_FILE.getBytes());
            }
            fos.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Method gets the path to last GPX file loaded based on value saved in private application
     * data saved on disk. Defaults to "/".
     *
     * @return path to gpx file
     */
    public String getGpxFilePath() {

        String filepath = DEFAULT_PATH_TO_GPX_FILE;

        try {
            FileInputStream fis = openFileInput(APP_DATA_CACHE_FILENAME);
            filepath = convertStreamToString(fis);
            if (filepath == null || filepath.equalsIgnoreCase("")) {
                filepath = DEFAULT_PATH_TO_GPX_FILE;
            }
            fis.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return filepath;
    }

    /**
     * Method reads inputstream to string.
     *
     * @param is
     * @return file contents
     * @throws Exception
     */
    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line);//.append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private void showMockLocationSettings() {
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.warning)
                .setMessage(R.string.message_allow_mock_location)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        startActivityForResult(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), REQUEST_MOCK_LOCATION);
                    }
                })
                .setCancelable(false)
                .create();
        alertDialog.show();
    }

    public static void hideKeyboard(View view, Activity context) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);
    }

    // ------------------------------ Permissions ------------------------------
    protected boolean hasPermission(String permission) {
        return PermissionUtil.hasPermission(this, permission);
    }

    protected boolean hasPermissions(String[] permissions) {
        return PermissionUtil.hasPermissions(this, permissions);
    }

    protected boolean couldShowRequestPermissionRationales(String[] permissions) {
        return PermissionUtil.couldShowRequestPermissionRationales(this, permissions);
    }

    protected boolean verifyPermissions(int[] grantResults) {
        return PermissionUtil.verifyPermissions(grantResults);
    }

    protected void requestAppPermission() {
        if (!hasPermissions(APP_PERMISSIONS)) {
            if (couldShowRequestPermissionRationales(APP_PERMISSIONS)) {
                showDialogExplainStoragePermission();
            } else {
                ActivityCompat.requestPermissions(this, APP_PERMISSIONS, REQUEST_APP_PERMISSION);
            }
        }
    }

    private void showDialogExplainStoragePermission() {
        StringBuilder appPermissions = new StringBuilder("");
        for (String permission : APP_PERMISSIONS) {
            appPermissions.append(PermissionUtil.manifestPermission2String(permission)).append(", ");
        }
//        Snackbar snackbar = Snackbar.make(getWindow().getDecorView().findViewById(android.R.id.content), String.format(getString(R.string.require_permission), appPermissions), Snackbar.LENGTH_INDEFINITE)
//                .setAction("ok", view -> ActivityCompat.requestPermissions(MainActivity.this, APP_PERMISSIONS, REQUEST_APP_PERMISSION));
//        snackbar.show();

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setMessage(String.format(getString(R.string.require_permission), appPermissions))
                .setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    ActivityCompat.requestPermissions(MainActivity.this, APP_PERMISSIONS, REQUEST_APP_PERMISSION);
                })
                .setCancelable(false)
                .create();
        dialog.show();
    }

    protected void showDialogSettings() {
//        Snackbar snackbar = Snackbar.make(this.findViewById(android.R.id.content), getString(R.string.require_permission_app_settings), Snackbar.LENGTH_INDEFINITE)
//                .setAction("ok", view -> openAppDetailsSetting());
//        snackbar.show();
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.require_permission_app_settings)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    openAppDetailsSetting();
                })
                .setCancelable(false)
                .create();
        dialog.show();
    }

    private void openAppDetailsSetting() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivityForResult(intent, REQUEST_PERMISSION_APP_SETTING);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_APP_PERMISSION) {
            if (verifyPermissions(grantResults)) {

            } else if (couldShowRequestPermissionRationales(APP_PERMISSIONS)) {
                requestAppPermission();
            } else {
                showDialogSettings();
            }
        }
    }
}