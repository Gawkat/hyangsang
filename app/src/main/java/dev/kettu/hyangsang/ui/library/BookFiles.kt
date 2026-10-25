package dev.kettu.hyangsang.ui.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/** The name of the file at [uri], as the app that has it shows it, if it says. */
fun ContentResolver.displayName(uri: Uri): String? =
    try {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: RuntimeException) {
        // Some apps share files they can't describe, or take the permission back
        null
    }
