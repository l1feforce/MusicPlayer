package gusev.spbstu.org.musicplayer.repository

import android.graphics.Bitmap
import android.os.Bundle
import androidx.fragment.app.Fragment
import gusev.spbstu.org.musicplayer.model.Song

class RetainedFragment: Fragment() {
    var songs = listOf<Song>()
    var covers = listOf<Bitmap>()
    var currentSong: Song? = null
    var currentPosition = 0
    var isPlayed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }
}