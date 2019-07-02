package gusev.spbstu.org.musicplayer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import gusev.spbstu.org.musicplayer.*
import gusev.spbstu.org.musicplayer.extensions.toDp
import gusev.spbstu.org.musicplayer.model.Song
import gusev.spbstu.org.musicplayer.repository.CustomMediaPlayer
import gusev.spbstu.org.musicplayer.repository.RetainedFragment
import gusev.spbstu.org.musicplayer.ui.adapters.CoverAdapter
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.mini_player.*
import kotlinx.android.synthetic.main.music_player.*
import kotlinx.coroutines.*

/**
 * К сожалению, я слишком поздно подумал насчет background play, и не успел сделать
 * все на MediaBrowserService.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private val REQUEST_CODE_GET_FOLDER = 1
    private val REQUEST_CODE_GET_READ_PERMISSION = 2

    private var listOfCovers = mutableListOf<Bitmap>()
    var seekFrom = ""
    var seekTo = ""
    val seekBarHandler = Handler()

    var isPlayClicked = false
    var isShuffleClicked = false
    var isRepeatClicked = false
    var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    var isOrientationChange = false

    lateinit var mediaPlayer: CustomMediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        page_viewer_album_arts.adapter = CoverAdapter(
            this,
            listOf(
                BitmapFactory.decodeResource(
                    this.resources,
                    R.drawable.empty_cover
                )
            )
        )
        val currentOrientation = this.requestedOrientation
        isOrientationChange = currentOrientation != previousOrientation
        previousOrientation = currentOrientation

        mediaPlayer = CustomMediaPlayer(applicationContext)
        setBottomSheetBehavior()
    }

    private fun getReadPermission() {
        Log.d("TAG", "try get the permissions")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE_GET_READ_PERMISSION
        )
    }

    private fun setBottomSheetBehavior() {
        bottomSheetBehavior = BottomSheetBehavior.from<ConstraintLayout>(music_player)
        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                mini_player.alpha = (1f - slideOffset)
                btn_dropdown.alpha = (slideOffset)
                page_viewer_album_arts.alpha = (slideOffset)
            }

            @SuppressLint("SwitchIntDef")
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        btn_dropdown.isClickable = false
                        btn_dropdown.isFocusable = false
                        mini_player.isClickable = true
                        mini_player.isFocusable = true
                        page_viewer_album_arts.isClickable = false
                        page_viewer_album_arts.isFocusable = false
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        btn_dropdown.isClickable = true
                        btn_dropdown.isFocusable = true
                        mini_player.isClickable = false
                        mini_player.isFocusable = false
                        page_viewer_album_arts.isClickable = true
                        page_viewer_album_arts.isFocusable = true
                    }
                }
            }
        })
    }

    private fun performOpenFolder() {
        val permissionStatus = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (permissionStatus == PackageManager.PERMISSION_DENIED) {
            getReadPermission()
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
        startActivityForResult(intent, REQUEST_CODE_GET_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_GET_FOLDER -> {
                val treeUri = data?.data ?: Uri.EMPTY
                val pickedDir = DocumentFile.fromTreeUri(this, treeUri)
                GlobalScope.launch {
                    Snackbar.make(
                        activity_main, getString(R.string.wait_for_songs),
                        Snackbar.LENGTH_SHORT
                    ).show()
                    val parseResult = parseSongs(pickedDir)
                    if (parseResult.isNotEmpty()) {
                        GlobalScope.launch(Dispatchers.Main) {
                            Log.d("TAG", "songs loading is completed")
                            isPlayClicked = false
                            btn_mini_play.setImageResource(R.drawable.ic_play_28)
                            btn_play.setImageResource(R.drawable.ic_play_48)
                            mediaPlayer.setSongsList(parseResult)
                            setupPageViewer()
                            updatePlayerUiAccordingToCurrentSong(
                                mediaPlayer.getCurrentSong()!!, 0
                            )
                        }
                    } else {
                        Log.d("TAG", "error during songs loading")
                        Snackbar.make(
                            activity_main, getString(R.string.there_are_no_audio),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private suspend fun parseSongs(dir: DocumentFile?): List<Song> {
        return GlobalScope.async {
            val listOfSongs = mutableListOf<Song>()
            listOfCovers.removeAll(listOfCovers)
            val files = dir?.listFiles() ?: arrayOf()
            for (i in 0 until files.size) {
                val file = files[i]
                if (file.type?.startsWith("audio") == true) {
                    val songInfo = MediaMetadataRetriever()
                    songInfo.setDataSource(this@MainActivity, file.uri)
                    val albumCover = getImage(songInfo)
                    var artist = ""
                    artist = try {
                        songInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    } catch (e: Exception) {
                        "Unknown"
                    }
                    var title = ""
                    title = try {
                        songInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    } catch (e: Exception) {
                        file.name ?: "Unknown"
                    }
                    val song = Song(
                        artist = artist,
                        title = title,
                        duration = songInfo.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toInt(),
                        albumCover = albumCover,
                        uri = file.uri,
                        position = i
                    )
                    listOfSongs.add(song)
                    listOfCovers.add(albumCover)
                }
            }
            return@async listOfSongs
        }.await()
    }

    private fun updatePlayerUiAccordingToCurrentSong(currentSong: Song, delta: Int) {
        tv_artist_name.text = currentSong.artist

        val title = currentSong.title
        tv_song_title.text = title
        Log.d("TAG", title)
        tv_mini_song_title.text = title

        img_mini_cover.setImageBitmap(listOfCovers[currentSong.position])

        seekBarHandler.removeCallbacksAndMessages(null)
        connectSeekBarToSong()
        page_viewer_album_arts.currentItem = page_viewer_album_arts.currentItem + delta
    }

    private fun setupPageViewer() {
        page_viewer_album_arts.adapter = CoverAdapter(this, listOfCovers)
        page_viewer_album_arts.currentItem = mediaPlayer.getCurrentSong()!!.position
        page_viewer_album_arts.clipToPadding = false
        page_viewer_album_arts.pageMargin = (-920).toDp()
        page_viewer_album_arts.setPageTransformer(false) { page, position ->
            val normalizedPosition = Math.abs(Math.abs(position) - 1)
            page.scaleX = normalizedPosition / 2 + 0.5f
            page.scaleY = normalizedPosition / 2 + 0.5f
        }
        page_viewer_album_arts.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }

            override fun onPageSelected(position: Int) {
                val currentSongPosition = mediaPlayer.getCurrentSong()!!.position
                if (position > currentSongPosition) {
                    val isPlayed = mediaPlayer.isPlaying()
                    mediaPlayer.skipNext()
                    revertPlay(isPlayed)
                    updatePlayerUiAccordingToCurrentSong(mediaPlayer.getCurrentSong()!!, 0)
                }
                if (position < currentSongPosition) {
                    val isPlayed = mediaPlayer.isPlaying()
                    mediaPlayer.skipPrevious()
                    revertPlay(isPlayed)
                    updatePlayerUiAccordingToCurrentSong(mediaPlayer.getCurrentSong()!!, 0)
                }
            }
        })
    }

    private fun connectSeekBarToSong() {
        val duration = mediaPlayer.getCurrentSong()!!.duration / 1000
        seekBar.max = duration

        runOnUiThread(object : Runnable {
            override fun run() {
                val currentPosition = mediaPlayer.getCurrentPosition() / 1000
                seekBar.progress = currentPosition
                refreshSeekBar(currentPosition, duration)
                seekBarHandler.postDelayed(this, 1000)
            }
        })

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress * 1000)
                    refreshSeekBar(progress, duration)
                }
            }
        })
    }

    private fun refreshSeekBar(currentPosition: Int, duration: Int) {
        seekTo = String.format(
            "-%02d:%02d", (duration - currentPosition) / 60,
            (duration - currentPosition) % 60
        )
        seekFrom = String.format("%02d:%02d", currentPosition / 60, currentPosition % 60)
        tv_mini_seek_to.text = seekTo
        tv_seek_to.text = seekTo
        tv_seek_from.text = seekFrom
    }

    private fun getImage(songInfo: MediaMetadataRetriever): Bitmap {
        return try {
            val data = songInfo.embeddedPicture
            BitmapFactory.decodeByteArray(
                data,
                0, data.size
            )
        } catch (e: Exception) {
            BitmapFactory.decodeResource(
                this.resources,
                R.drawable.empty_cover
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_GET_READ_PERMISSION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("TAG", "permissions granted")
                    performOpenFolder()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        val retainedFragment = RetainedFragment()
        supportFragmentManager.beginTransaction().add(
            retainedFragment,
            "data"
        ).commitAllowingStateLoss()
        retainedFragment.apply {
            if (mediaPlayer.isInitialized()) {
                currentSong = mediaPlayer.getCurrentSong()
                covers = listOfCovers
                songs = mediaPlayer.getSongList()
                currentPosition = mediaPlayer.getCurrentPosition()
                isPlayed = mediaPlayer.isPlaying()
                mediaPlayer.release()
                seekBarHandler.removeCallbacksAndMessages(null)
            }
        }
        Log.d("TAG", "onSaveInstanceState")
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        val retainedFragment = supportFragmentManager.findFragmentByTag("data") as RetainedFragment
        setAlphas()
        if (!::mediaPlayer.isInitialized) mediaPlayer = CustomMediaPlayer(applicationContext)
        if (retainedFragment.songs.isEmpty() ||
            !isOrientationChange
        ) return
        mediaPlayer.restorePlayerState(
            retainedFragment.songs, retainedFragment.currentSong!!,
            retainedFragment.currentPosition
        )
        if (retainedFragment.isPlayed) onClick(btn_play)

        listOfCovers = retainedFragment.covers.toMutableList()
        setupPageViewer()
        updatePlayerUiAccordingToCurrentSong(mediaPlayer.getCurrentSong()!!, 0)
        connectSeekBarToSong()
    }

    private fun setAlphas() {
        when (bottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_COLLAPSED -> {
                mini_player.alpha = (1f)
                btn_dropdown.alpha = (0f)
                page_viewer_album_arts.alpha = (0f)
            }
            BottomSheetBehavior.STATE_EXPANDED -> {
                mini_player.alpha = (0f)
                btn_dropdown.alpha = (1f)
                page_viewer_album_arts.alpha = (1f)
            }
        }
    }

    fun onClick(v: View) {
        when (v.id) {
            R.id.btn_dropdown -> {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            R.id.btn_play -> {
                if (!::mediaPlayer.isInitialized || !mediaPlayer.isInitialized()) return
                if (isPlayClicked) {
                    mediaPlayer.pause()
                    btn_mini_play.setImageResource(R.drawable.ic_play_28)
                    btn_play.setImageResource(R.drawable.ic_play_48)
                } else {
                    mediaPlayer.play()
                    btn_mini_play.setImageResource(R.drawable.ic_pause_28)
                    btn_play.setImageResource(R.drawable.ic_pause_48)
                }
                isPlayClicked = !isPlayClicked
            }
            R.id.btn_mini_play -> {
                onClick(btn_play)
            }
            R.id.btn_skip_previous -> {
                if (!::mediaPlayer.isInitialized || !mediaPlayer.isInitialized()) return
                val isPlayed = mediaPlayer.isPlaying()
                mediaPlayer.skipPrevious()
                revertPlay(isPlayed)
                updatePlayerUiAccordingToCurrentSong(mediaPlayer.getCurrentSong()!!, -1)

            }
            R.id.btn_skip_next -> {
                if (!::mediaPlayer.isInitialized || !mediaPlayer.isInitialized()) return
                val isPlayed = mediaPlayer.isPlaying()
                mediaPlayer.skipNext()
                revertPlay(isPlayed)
                updatePlayerUiAccordingToCurrentSong(mediaPlayer.getCurrentSong()!!, 1)
            }
            R.id.btn_mini_skip_next -> {
                onClick(btn_skip_next)
            }
            R.id.btn_open_folder -> {
                performOpenFolder()
            }
            R.id.btn_shuffle -> {
                isShuffleClicked = revertView(btn_shuffle, isShuffleClicked)
            }
            R.id.btn_repeat -> {
                isRepeatClicked = revertView(btn_repeat, isRepeatClicked)
            }
            R.id.tv_mini_song_title -> {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun revertPlay(isPlayed: Boolean) {
        if (isPlayed) mediaPlayer.play()
        else onClick(btn_play)
    }

    private fun revertView(view: ImageButton, isReverted: Boolean): Boolean {
        if (!isReverted) {
            view.setColorFilter(
                ContextCompat.getColor(
                    this,
                    R.color.colorAccent
                ), android.graphics.PorterDuff.Mode.MULTIPLY
            )
            view.background = ContextCompat.getDrawable(
                this,
                R.drawable.bg_btn_colored
            )
        } else {
            view.setColorFilter(
                ContextCompat.getColor(
                    this,
                    R.color.colorPrimaryDark
                ), android.graphics.PorterDuff.Mode.MULTIPLY
            )
            view.background = ContextCompat.getDrawable(
                this,
                R.drawable.bg_btn_transparent
            )
        }
        return !isReverted
    }
}