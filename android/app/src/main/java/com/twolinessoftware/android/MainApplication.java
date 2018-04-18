package com.twolinessoftware.android;

import android.app.Application;
import android.os.StrictMode;


public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        /*
        Never
        Never ever
        Never ever ever
        Never ever ever EVER EVERERER do this in production code.

        This is to get around android.os.FileUriExposedException in>=API24
        The correct solution is to use a content:// URI with a FileProvider.

         However since this app is only used for test purposes I'm using this work-around
        */
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
    }
}
