package com.twolinessoftware.android

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log

class MockUtils {
    companion object {
        private val TAG: String = MockUtils::class.java.getSimpleName()

        /**
         * Check if mock location is enabled on developer options.
         *
         * @return true if mock location is enabled else it returns false.
         */
        fun canMockLocation(context: Context): Boolean {
            var isEnabled = false
            try {
                isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val opsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                    opsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(),
                            BuildConfig.APPLICATION_ID) == AppOpsManager.MODE_ALLOWED
                } else {
                    return !Settings.Secure.getString(context.getContentResolver(),
                            Settings.Secure.ALLOW_MOCK_LOCATION).equals("0")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Mock location is not enabled.")
            }
            return isEnabled
        }
    }
}