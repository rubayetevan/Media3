package com.splyza.media3

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import kotlin.math.max


@SuppressLint("ClickableViewAccessibility")
class SplyzaVideoPlayer : ConstraintLayout {
    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    companion object {
        private const val TAG = "SplyzaVideoPlayer"
        private const val CONTROLLER_HIDE_TIMEOUT_MS = 5000L
        const val VIDEO_SOURCE = "videoSource"
        const val KEY_POSITION = "position"
        const val KEY_AUTO_PLAY = "auto_play"
    }

    private val activity: Activity = context as Activity
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var gestureView: View
    private var controllerView: View
    private var playerView: PlayerView
    private var fullScreenBTN: ImageButton
    private var playPauseBTN: ImageButton
    private var replayBTN: ImageButton
    private var forwardBTN: ImageButton
    private var closeBTN: ImageButton
    private var timeBar: TimeBar
    private var positionTV: TextView
    private var durationTV: TextView


    private var player: ExoPlayer? = null
    private var startAutoPlay = false
    private var startPosition: Long = 0

    private var handler: Handler? = null
    private var isScrubbing = false
    private var intent: Intent? = null

    init {
        val rootView = LayoutInflater.from(context).inflate(R.layout.player_view, this, true)
        playerView = rootView.findViewById(R.id.playerView)
        gestureView = rootView.findViewById(R.id.gestureView)
        controllerView = rootView.findViewById(R.id.controllerView)
        fullScreenBTN = rootView.findViewById(R.id.fullscreen)
        playPauseBTN = rootView.findViewById(R.id.playPauseBtn)
        replayBTN = rootView.findViewById(R.id.replayBTN)
        forwardBTN = rootView.findViewById(R.id.forwardBTN)
        closeBTN = rootView.findViewById(R.id.close)
        timeBar = rootView.findViewById(R.id.timeBar)
        positionTV = rootView.findViewById(R.id.position)
        durationTV = rootView.findViewById(R.id.duration)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun onCreate(savedInstanceState: Bundle?, intent: Intent?) {
        this.intent = intent
        playerView.setErrorMessageProvider(PlayerErrorMessageProvider())
        playerView.requestFocus()
        hideController()
        gestureView.setOnTouchListener { _, motionEvent ->
            scaleGestureDetector.onTouchEvent(motionEvent)
            if(!controllerView.isVisible) {
                controllerView.visibility = View.VISIBLE
                hideController()
            }
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
        fullScreenBTN.setOnClickListener {
            val currentOrientation = resources.configuration.orientation
            activity.requestedOrientation =
                if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
        }
        scaleGestureDetector = ScaleGestureDetector(
            activity, ScaleGestureListener(playerView)
        )

        playPauseBTN.setOnClickListener {
            if (player?.isPlaying == true) {
                player?.pause()
                playPauseBTN.setBackgroundResource(R.drawable.baseline_play_arrow_24)
            } else {
                player?.play()
                playPauseBTN.setBackgroundResource(R.drawable.baseline_pause_24)
            }
        }

        replayBTN.setOnClickListener {

            player?.let {
                if (it.contentPosition >= 5000L) {
                    it.seekTo(it.contentPosition - 5000L)
                }
            }
        }

        forwardBTN.setOnClickListener {
            player?.let {
                if (it.contentPosition <= it.duration - 5000L) {
                    it.seekTo(it.contentPosition + 5000L)
                }
            }
        }

        closeBTN.setOnClickListener {
            activity.finish()
        }
    }

    private fun hideController() {
        controllerView.postDelayed({
            controllerView.visibility = View.INVISIBLE
        }, CONTROLLER_HIDE_TIMEOUT_MS)
    }

    @Suppress("unused")
    fun onNewIntent(intent: Intent?) {
        this.intent = intent
        Log.d(TAG,"onNewIntent::intent =$intent")
        releasePlayer()
        clearStartPosition()
    }

    private fun releasePlayer() {
        player?.let {
            updateStartPosition()
            it.release()
            player = null
            playerView.player = null
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

    fun onStart() {
        initializePlayer()
        playerView.onResume()
    }

    fun onResume() {
        if (player == null) {
            initializePlayer()
            playerView.onResume()
        }
    }

    fun onStop() {
        playerView.onPause()
        releasePlayer()
    }

    fun onSaveInstanceState(outState: Bundle) {
        updateStartPosition()
        outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay)
        outState.putLong(KEY_POSITION, startPosition)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer(): Boolean {
        val url = intent?.getStringExtra(VIDEO_SOURCE)

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
            playerView.player = player
            timeBar.addListener(playerSeekBarListener)
        }
        player?.setMediaItem(mediaItem)
        player?.prepare()
        if (startPosition != C.TIME_UNSET) {
            player?.seekTo(startPosition)
        }
        if (startAutoPlay) {
            playPauseBTN.setBackgroundResource(R.drawable.baseline_pause_24)
        } else {
            playPauseBTN.setBackgroundResource(R.drawable.baseline_play_arrow_24)
        }

        return true
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private val updateProgressAction = object : Runnable {
        override fun run() {
            if (!isScrubbing) {
                player?.let {
                    positionTV.text = getPlayerTime(it.currentPosition)
                    timeBar.setPosition(it.contentPosition)
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
                    timeBar.setDuration(it)
                    durationTV.text = getPlayerTime(it)
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

}