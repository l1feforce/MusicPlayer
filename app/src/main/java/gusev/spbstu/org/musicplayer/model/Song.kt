package gusev.spbstu.org.musicplayer.model

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.net.Uri

data class Song(
    val artist: String = "Artist", val title: String = "Song Name",
    val duration: Int, val albumCover: Bitmap? = null,
    val uri: Uri, val position: Int
) {
}