package com.twolinessoftware.android.util;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;


public  class PermissionUtil {
    public static boolean hasPermission(@NonNull Activity activity, @NonNull String permission) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasPermissions(@NonNull Activity activity, String[] permissions) {
        for (String permission : permissions) {
            if (!hasPermission(activity, permission))
                return false;
        }
        return true;
    }

    public static boolean couldShowRequestPermissionRationale(@NonNull Activity activity, String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    public static boolean couldShowRequestPermissionRationales(@NonNull Activity activity, String[] permissions) {
        for (String permission : permissions) {
            if (!couldShowRequestPermissionRationale(activity, permission))
                return false;
        }
        return true;
    }

    public static boolean verifyPermissions(int[] grantResults) {
        if (grantResults.length > 0) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED)
                    return false;
            }
        } else {
            return false;
        }
        return true;
    }

    /**
     * ex: "android.permission.READ_EXTERNAL_STORAGE" -> "READ EXTERNAL STORAGE"
     */
    public static String manifestPermission2String(String permissionName) {
        return permissionName.split("android.permission.")[1].replace("_", " ");
    }
}