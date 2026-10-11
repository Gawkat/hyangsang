package dev.kettu.hyangsang.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.kettu.hyangsang.HyangsangApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sets stored settings from adb, for testing. Only in debug builds:
 *
 *     adb shell am broadcast -n dev.kettu.hyangsang/.debug.DebugPrefsReceiver --ei default_feeds_seen 1
 *
 * Without the extra, the stored version is cleared, as for users from before it was stored.
 */
class DebugPrefsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val version = if (intent.hasExtra(DEFAULT_FEEDS_SEEN)) intent.getIntExtra(DEFAULT_FEEDS_SEEN, 0) else null
        val prefs = (context.applicationContext as HyangsangApplication).userPreferencesRepository
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                prefs.setDefaultFeedsSeen(version)
                Log.i(TAG, "default_feeds_seen set to ${version ?: "unset"}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DebugPrefsReceiver"
        const val DEFAULT_FEEDS_SEEN = "default_feeds_seen"
    }
}
