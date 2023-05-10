package com.splyza.media

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.TimeBar
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.splyza.media.databinding.PlayerViewBinding
import kotlin.math.max


@SuppressLint("ClickableViewAccessibility")
class VideoPlayer : ConstraintLayout {
    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    companion object {
        private const val TAG = "VideoPlayer"
        private const val CONTROLLER_HIDE_TIMEOUT_MS = 5000L
        const val KEY_VIDEO_SOURCE = "videoSource"
        const val KEY_POSITION = "position"
        const val KEY_AUTO_PLAY = "auto_play"
        const val KEY_PLAYBACK_SPEED = "playback_speed"
        const val KEY_SCALE_FACTOR = "scale_factor"

        private val speedMap: MutableMap<String, Float> = mutableMapOf(
            "x4" to 4f,
            "x2" to 2f,
            "x1.5" to 1.5f,
            "x1" to 1f,
            "x1/2" to 1 / 2f,
            "x1/4" to 1 / 4f,
            "x1/8" to 1 / 8f,
            "x1/16" to 1 / 16f
        )

        private var duration = 15000L
        const val INTERVAL = 1L
    }

    private val activity: Activity = context as Activity
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var player: ExoPlayer? = null
    private var startAutoPlay = false
    private var startPosition: Long = 0
    private var playBackSpeed: String = "x1"
    private var handler: Handler? = null
    private var isScrubbing = false
    private var intent: Intent? = null

    private val binding: PlayerViewBinding =
        PlayerViewBinding.inflate(LayoutInflater.from(context), this, true)


    @SuppressLint("ClickableViewAccessibility")
    fun onCreate(savedInstanceState: Bundle?, intent: Intent?) {
        this.intent = intent
        binding.playerView.setErrorMessageProvider(PlayerErrorMessageProvider())
        binding.playerView.requestFocus()

        val scaleGestureListener = ScaleGestureListener(binding.playerView)

        savedInstanceState?.let {
            startAutoPlay = it.getBoolean(KEY_AUTO_PLAY)
            startPosition = it.getLong(KEY_POSITION)
            playBackSpeed = it.getString(KEY_PLAYBACK_SPEED, "x1")
            scaleGestureListener.setScaleFactor(it.getFloat(KEY_SCALE_FACTOR))

        } ?: run {
            clearStartPosition()
        }
        setupPlayerController(scaleGestureListener)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun setupPlayerController(scaleGestureListener: ScaleGestureListener) {

        scaleGestureDetector = ScaleGestureDetector(
            context, scaleGestureListener
        )

        val playBackSpeedAlertDialogBuilder = AlertDialog.Builder(context)
        playBackSpeedAlertDialogBuilder.setTitle("Change Speed")
        val speedList = speedMap.keys.toTypedArray()
        playBackSpeedAlertDialogBuilder.setItems(speedList) { dialog, which ->
            playBackSpeed = speedList[which]
            binding.speedTV.text = playBackSpeed
            speedMap[playBackSpeed]?.let { player?.setPlaybackSpeed(it) }
        }
        val playBackSpeedAlertDialog = playBackSpeedAlertDialogBuilder.create()

        binding.gestureView.setOnTouchListener { _, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_MOVE -> {
                    scaleGestureDetector.onTouchEvent(motionEvent)
                }

                MotionEvent.ACTION_DOWN -> {
                    TransitionManager.beginDelayedTransition(
                        binding.controllerView,
                        AutoTransition()
                    )
                    binding.controllerView.visibility = if (binding.controllerView.isVisible) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                }
            }
            true
        }

        binding.fullscreen.setOnClickListener {
            val currentOrientation = resources.configuration.orientation
            activity.requestedOrientation =
                if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
        }



        binding.playPauseBtn.setOnClickListener {

            player?.let {
                playPause()
            }

        }

        binding.replayBTN.setOnClickListener {

            player?.let {
                if (it.contentPosition >= 5000L) {
                    it.seekTo(it.contentPosition - 5000L)
                }
            }
        }

        binding.forwardBTN.setOnClickListener {
            player?.let {
                if (it.contentPosition <= it.duration - 5000L) {
                    it.seekTo(it.contentPosition + 5000L)
                }
            }
        }

        binding.close.setOnClickListener {
            activity.finish()
        }

        binding.speedTV.setOnClickListener {
            playBackSpeedAlertDialog.show()
        }

        binding.pauseEditBtn.setOnClickListener {
            timer.start()
        }
    }

    private fun playPause() {
        player?.let {
            it.playWhenReady = !it.playWhenReady
            if (it.playWhenReady) {
                binding.playPauseBtn.setBackgroundResource(R.drawable.baseline_pause_24)
            } else {
                binding.playPauseBtn.setBackgroundResource(R.drawable.baseline_play_arrow_24)
            }
        }
    }

    private fun hideController() {
        binding.controllerView.postDelayed({
            binding.controllerView.visibility = View.INVISIBLE
        }, CONTROLLER_HIDE_TIMEOUT_MS)
    }

    @Suppress("unused")
    fun onNewIntent(intent: Intent?) {
        this.intent = intent
        Log.d(TAG, "onNewIntent::intent =$intent")
        releasePlayer()
        clearStartPosition()
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
        playBackSpeed = "x1"
    }

    fun onStart() {
        initializePlayer()
        binding.playerView.onResume()
    }

    fun onResume() {
        if (player == null) {
            initializePlayer()
            binding.playerView.onResume()
        }
    }

    fun onStop() {
        binding.playerView.onPause()
        releasePlayer()
    }

    fun onSaveInstanceState(outState: Bundle) {
        updateStartPosition()
        outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay)
        outState.putLong(KEY_POSITION, startPosition)
        outState.putString(KEY_PLAYBACK_SPEED, playBackSpeed)
        outState.putFloat(KEY_SCALE_FACTOR, ScaleGestureListener.scaleFactor)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer(): Boolean {
        val url = intent?.getStringExtra(KEY_VIDEO_SOURCE)

        if (url.isNullOrBlank()) return false

        val videoUri: Uri = Uri.parse(url)
        val mediaItem = MediaItem.fromUri(videoUri)

        if (player == null) {
            if (handler == null) {
                handler = Handler(Looper.getMainLooper())
            }
            val playerBuilder = ExoPlayer.Builder(activity)
            player = playerBuilder.build()
            player?.addListener(playerEventListener)
            player?.addAnalyticsListener(EventLogger())
            player?.setAudioAttributes(AudioAttributes.DEFAULT, true)
            player?.playWhenReady = startAutoPlay
            binding.playerView.player = player
            binding.timeBar.addListener(playerSeekBarListener)
        }
        player?.setMediaItem(mediaItem)
        player?.prepare()
        if (startPosition != C.TIME_UNSET) {
            player?.seekTo(startPosition)
        }

        if (playBackSpeed != "x1") {
            binding.speedTV.text = playBackSpeed
            speedMap[playBackSpeed]?.let { player?.setPlaybackSpeed(it) }
        }

        return true
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private val updateProgressAction = object : Runnable {
        override fun run() {
            if (!isScrubbing) {
                player?.let {
                    binding.position.text = getPlayerTime(it.currentPosition)
                    binding.timeBar.setPosition(it.contentPosition)
                }
            }
            handler?.post(this)
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private val playerEventListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                player?.duration?.let {
                    binding.timeBar.setDuration(it)
                    binding.duration.text = getPlayerTime(it)
                }
                handler?.post(updateProgressAction)
            } else {
                handler?.removeCallbacks(updateProgressAction)
            }

            if (playbackState == Player.STATE_ENDED) {
                player?.playWhenReady = false
                player?.seekTo(0L)
            }

            if (player?.playWhenReady == true) {
                binding.playPauseBtn.setBackgroundResource(R.drawable.baseline_pause_24)
            } else {
                binding.playPauseBtn.setBackgroundResource(R.drawable.baseline_play_arrow_24)
            }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            super.onIsLoadingChanged(isLoading)
            if (isLoading) {
                binding.progressBar.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun getPlayerTime(mills: Long): String {
        val hours = mills / 1000 / 60 / 60
        val minutes = mills / 1000 / 60 % 60
        val seconds = (mills / 1000) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        requestLayout()
    }


    private val timer = object : CountDownTimer(duration, INTERVAL) {
        override fun onTick(millisUntilFinished: Long) {
            val progress = 100 - (millisUntilFinished / duration.toDouble() * 100).toInt()
            binding.pauseProgressBar.progress = progress
        }

        override fun onFinish() {
            binding.pauseProgressBar.progress = 100
        }
    }


}