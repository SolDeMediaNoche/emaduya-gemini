
package com.wave.music

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

object MusicRepository {


    fun getSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            collection, projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndex(
                MediaStore.Audio.Media.RELATIVE_PATH
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol).orEmpty()
                val fileName = cursor.getString(nameCol).orEmpty()
                val path = if (pathCol >= 0) {
                    cursor.getString(pathCol).orEmpty()
                } else ""

                val normalizedName = fileName.trim().uppercase()
                val normalizedTitle = title.trim().uppercase()
                val normalizedPath = path.lowercase()

                // Excluir grabaciones y notas de voz conocidas.
                val isRecording =
                    normalizedName.startsWith("AUD-") ||
                            normalizedTitle.startsWith("AUD-") ||
                            normalizedName.startsWith("PTT-") ||
                            normalizedPath.contains("whatsapp/medi/whatsapp audio") ||
                            normalizedPath.contains("whatsapp voice notes")

                if (isRecording) continue

                val artist = cursor.getString(artistCol)
                    ?.takeUnless {
                        it.isBlank() || it == "<unknown>"
                    } ?: "Artista desconocido"

                val album = cursor.getString(albumCol)
                    ?.takeUnless {
                        it.isBlank() || it == "<unknown>"
                    } ?: "Álbum desconocido"

                songs.add(
                    Song(
                        id = id,
                        title = title.ifBlank { fileName },
                        artist = artist,
                        album = album,
                        uri = ContentUris.withAppendedId(collection, id),
                        duration = cursor.getLong(durationCol)
                    )
                )
            }
        }

        return songs
    }

}

