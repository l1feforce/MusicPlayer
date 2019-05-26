package gusev.spbstu.org.musicplayer.repository

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import gusev.spbstu.org.musicplayer.model.Song
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.text.FieldPosition

class CustomMediaPlayer(private val context: Context) {
    private lateinit var mediaPlayer: MediaPlayer
    private var listOfSongs = listOf<Song>()
    private lateinit var currentSong: Song

    fun isPlaying() =  if (::mediaPlayer.isInitialized) mediaPlayer.isPlaying else false

    fun getCurrentSong() = if (::currentSong.isInitialized) currentSong else null

    fun getCurrentPosition() = mediaPlayer.currentPosition

    fun seekTo(msec: Int) = mediaPlayer.seekTo(msec)

    fun setSongsList(songs: List<Song>) {
        listOfSongs = songs
        if (songs.isEmpty()) throw IllegalArgumentException("Empty songs list")
        if (::mediaPlayer.isInitialized) release()
        mediaPlayer = MediaPlayer()
        currentSong = listOfSongs.first()
        mediaPlayer.setDataSource(context, currentSong.uri)
        mediaPlayer.prepare()
        Log.d("TAG", "Songs are prepared")
    }

    fun restorePlayerState(songs: List<Song>, currSong: Song, currPosition: Int) {
        listOfSongs = songs
        if (songs.isEmpty()) throw IllegalArgumentException("Empty songs list")
        if (::mediaPlayer.isInitialized) release()
        mediaPlayer = MediaPlayer()
        currentSong = currSong
        mediaPlayer.setDataSource(context, currentSong.uri)
        mediaPlayer.prepare()
        mediaPlayer.seekTo(currPosition)
    }

    fun setCurrentSong(song: Song) {
        currentSong = song
    }

    fun getSongList() : List<Song> = listOfSongs

    fun play() {
        if (!::mediaPlayer.isInitialized) return
        mediaPlayer.start()
    }

    fun pause() {
        if (!::mediaPlayer.isInitialized) return
        mediaPlayer.pause()
    }

    fun skipNext(): Boolean {
        if (!::mediaPlayer.isInitialized) return false
        if (currentSong.position + 1 in 0 until listOfSongs.size) {
            shiftSong(1)
            mediaPlayer.start()
            return true
        }
        return false
    }

    fun skipPrevious(): Boolean {
        if (!::mediaPlayer.isInitialized) throw IllegalStateException("Firstly set songs list")
        if (currentSong.position - 1 in 0 until listOfSongs.size) {
            shiftSong(- 1)
            mediaPlayer.start()
            return true
        }
        return false
    }

    private fun shiftSong(delta: Int) {
        mediaPlayer.release()
        mediaPlayer = MediaPlayer()
        currentSong = listOfSongs[currentSong.position + delta]
        mediaPlayer.setDataSource(context, currentSong.uri)
        mediaPlayer.prepare()
    }

    fun release() {
        mediaPlayer.release()
    }

    fun isInitialized(): Boolean = ::mediaPlayer.isInitialized
}