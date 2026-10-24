
package com.wave.music

import android.content.Context
import android.net.Uri

object ArtworkStore {
    private const val PREFS = "wave_artwork"
    private fun key(type: String, id: Long) = "${type}_$id"

    fun get(context: Context, type: String, id: Long): Uri? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(type, id), null) ?: return null
        return runCatching { Uri.parse(value) }.getOrNull()
    }

    fun set(context: Context, type: String, id: Long, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(type, id), uri.toString())
            .apply()
    }

    fun clear(context: Context, type: String, id: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key(type, id))
            .apply()
    }
}
