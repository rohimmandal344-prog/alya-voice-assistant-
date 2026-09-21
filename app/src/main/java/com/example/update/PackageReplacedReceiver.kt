package com.example.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver triggered when the application is updated on device (MY_PACKAGE_REPLACED).
 * Clears stale caches and logs package replacement to ensure newly installed build loads cleanly.
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i(TAG, "Application package replaced/updated successfully. Purging stale caches.")
            try {
                context.cacheDir?.deleteRecursively()
                context.codeCacheDir?.deleteRecursively()
                Log.i(TAG, "Cache directories cleared after MY_PACKAGE_REPLACED signal.")
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing cache on package replaced: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "PackageReplacedReceiver"
    }
}
