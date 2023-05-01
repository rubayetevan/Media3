package com.splyza.media3

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.TimeBar
import com.splyza.media3.Keys.KEY_AUTO_PLAY
import com.splyza.media3.Keys.KEY_POSITION
import com.splyza.media3.databinding.ActivityPlayerBinding
import com.splyza.media3.databinding.PlayerControllerBinding
import kotlin.math.max


class PlayerActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "PlayerActivity"
        private const val CONTROLLER_HIDE_TIMEOUT_MS = 10000L
    }

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var controllerBinding: PlayerControllerBinding
    private var player: ExoPlayer? = null
    private var startAutoPlay = false
    private var startPosition: Long = 0
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var handler: Handler? = null
    private var isScrubbing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initImmersiveMode()
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        val view = binding.root
        controllerBinding = PlayerControllerBinding.bind(view)
        setContentView(view)
        setupVideoPlayer(savedInstanceState)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupVideoPlayer(savedInstanceState: Bundle?) {
        binding.playerView.setErrorMessageProvider(PlayerErrorMessageProvider())
        binding.playerView.requestFocus()
        hideController()
        controllerBinding.gestureView.setOnTouchListener { view, motionEvent ->
            scaleGestureDetector?.onTouchEvent(motionEvent)
            controllerBinding.controllerView.visibility = View.VISIBLE
            hideController()
            true
        }

        savedInstanceState?.let {
            startAutoPlay = it.getBoolean(KEY_AUTO_PLAY)
            startPosition = it.getLong(KEY_POSITION)
        } ?: run {
            clearStartPosition()
        }
        setupPlayerController()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun setupPlayerController() {
        controllerBinding.fullscreen.setOnClickListener {
            Log.d(TAG, "controllerBinding.fullscreenTV.setOnClickListener::clicked")
            val currentOrientation = resources.configuration.orientation
            requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        scaleGestureDetector = ScaleGestureDetector(
            this@PlayerActivity, ScaleGestureListener(binding.playerView)
        )

        controllerBinding.playPauseBtn.setOnClickListener {
            if (player?.isPlaying == true) {
                player?.pause()
                controllerBinding.playPauseBtn.setBackgroundResource(R.drawable.baseline_play_arrow_24)
            } else {
                player?.play()
                controllerBinding.playPauseBtn.setBackgroundResource(R.drawable.baseline_pause_24)
            }
        }



        controllerBinding.replayBTN.setOnClickListener {

            player?.let {
                if (it.contentPosition >= 5000L) {
                    it.seekTo(it.contentPosition - 5000L)
                }
            }
        }
        controllerBinding.forwardBTN.setOnClickListener {
            player?.let {
                if (it.contentPosition <= it.duration - 5000L) {
                    it.seekTo(it.contentPosition + 5000L)
                }
            }
        }
        controllerBinding.close.setOnClickListener {
            finish()
        }
    }


    private fun initImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        releasePlayer()
        clearStartPosition()
        setIntent(intent)
    }

    private fun releasePlayer() {
        player?.let {
            updateStartPosition()
            it.release()
            player = null
            binding.playerView.player = null
        }
        handler?.removeCallbacks(updateProgressAction)
    }

    private fun updateStartPosition() {
        player?.let {
            startAutoPlay = it.playWhenReady
            startPosition = max(0, it.contentPosition)
        }
    }

    private fun clearStartPosition() {
        startAutoPlay = true
        startPosition = C.TIME_UNSET
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
        binding.playerView.onResume()
    }

    override fun onResume() {
        super.onResume()
        if (player == null) {
            initializePlayer()
            binding.playerView.onResume()
        }
    }

    override fun onStop() {
        super.onStop()
        binding.playerView.onPause()
        releasePlayer()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer(): Boolean {
        val url = intent.getStringExtra(Keys.VIDEO_SOURCE)

        if (url.isNullOrBlank()) return false

        val videoUri: Uri = Uri.parse(url)
        val mediaItem = MediaItem.fromUri(videoUri)

        if (player == null) {
            if (handler == null) {
                handler = Handler(Looper.getMainLooper())
            }
            val playerBuilder = ExoPlayer.Builder(this@PlayerActivity)
            player = playerBuilder.build()
            player?.addListener(playerEventListener)
            player?.addAnalyticsListener(EventLogger())
            player?.setAudioAttributes(AudioAttributes.DEFAULT, true)
            player?.playWhenReady = startAutoPlay
            binding.playerView.player = player
            controllerBinding.timeBar.addListener(playerSeekBarListener)
        }
        player?.setMediaItem(mediaItem)
        player?.prepare()
        if (startPosition != C.TIME_UNSET) {
            player?.seekTo(startPosition)
        }
        if (startAutoPlay) {
            controllerBinding.playPauseBtn.setBackgroundResource(R.drawable.baseline_pause_24)
        } else {
            controllerBinding.playPauseBtn.setBackgroundResource(R.drawable.baseline_play_arrow_24)
        }

        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        updateStartPosition()
        outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay)
        outState.putLong(KEY_POSITION, startPosition)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return binding.playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }


    private fun hideController() {
        controllerBinding.controllerView.postDelayed({
            controllerBinding.controllerView.visibility = View.INVISIBLE
        }, CONTROLLER_HIDE_TIMEOUT_MS)
    }


    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private val updateProgressAction = object : Runnable {
        override fun run() {
            if (!isScrubbing) {
                player?.let {
                    controllerBinding.position.text = getPlayerTime(it.currentPosition)
                    controllerBinding.timeBar.setPosition(it.contentPosition)
                }
            }
            handler?.post(this)
        }
    }

    @UnstableApi
    private val playerSeekBarListener = object : TimeBar.OnScrubListener {

        override fun onScrubStart(timeBar: TimeBar, position: Long) {
            isScrubbing = true
            player?.playWhenReady = false
            handler?.removeCallbacks(updateProgressAction)
        }

        override fun onScrubMove(timeBar: TimeBar, position: Long) {
            timeBar.setPosition(position)
        }

        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
            isScrubbing = false
            if (!canceled) {
                player?.seekTo(position)
            }
            player?.playWhenReady = true
            handler?.post(updateProgressAction)
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private val playerEventListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                player?.duration?.let {
                    controllerBinding.timeBar.setDuration(it)
                    controllerBinding.duration.text = getPlayerTime(it)
                }
                handler?.post(updateProgressAction)
            } else {
                handler?.removeCallbacks(updateProgressAction)
            }
        }
    }

    private fun getPlayerTime(mills: Long): String {
        val hours = mills / 1000 / 60 / 60
        val minutes = mills / 1000 / 60 % 60
        val seconds = (mills / 1000) % 60
        return String.format("%d:%02d:%02d", hours, minutes, seconds)
    }

}