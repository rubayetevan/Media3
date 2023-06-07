package com.splyza.media

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
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
import java.util.TreeSet
import kotlin.math.abs
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
        const val KEY_VIDEO_SOURCE = "videoSource"
        private const val KEY_POSITION = "position"
        private const val KEY_AUTO_PLAY = "auto_play"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_SCALE_FACTOR = "scale_factor"
        private const val KEY_AUTO_PAUSE_INTERVALS = "auto_pause_intervals"
        private const val KEY_TAG_TIMES = "tag_times"
        private const val KEY_TAG_TIME_INDEX = "tag_time_index"
        private const val KEY_AUTO_PAUSE_INTERVAL_INDEX = "auto_pause_interval_index"
        private const val KEY_CURRENT_TAG_TIME = "current_tag_time"
        private const val KEY_PAUSE_EDIT_ENABLED = "pause_enabled"
        private const val KEY_AUTO_PAUSE_EDIT_ENABLED = "auto_pause_enabled"
        private const val KEY_AUTO_PAUSE_TIMER_REMAINING_TIME = "auto_pause_timer_remaining_time"

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
    private var pauseEditEnabled: Boolean = false
    private var autoPauseEditEnabled: Boolean = false
    private val binding: PlayerViewBinding =
        PlayerViewBinding.inflate(LayoutInflater.from(context), this, true)

    private val pencilPauseDrawable =
        AppCompatResources.getDrawable(context, R.drawable.pencil_pause)
    private val pencilPauseInactiveDrawable =
        AppCompatResources.getDrawable(context, R.drawable.pencil_pause_inactive)

    private var autoPauseIntervals = sortedSetOf<Long>()
    private var tagTimes = sortedSetOf<Long>()

    private var timer: CountDownTimer? = null
    private var currentTagTime: Long = Long.MAX_VALUE
    private var tagTimeIndex = 0
    private var autoPauseIntervalIndex = -1
    private var autoPauseTimerRemainingTime: Long = 0L

    private val isTagTimeVisited = mutableMapOf<Long, Boolean>()

    fun setTagTimes(timeList: List<Long>) {
        tagTimes.clear()
        timeList.forEach {
            tagTimes.add(it)
            isTagTimeVisited[it] = false
        }
    }

    fun setAutoPauseIntervals(timeList: List<Long>) {
        autoPauseIntervals.clear()
        timeList.forEach {
            autoPauseIntervals.add(it)
        }
    }


    init {
        binding.pauseProgressBar.apply {
            progressTintList = ColorStateList.valueOf(Color.WHITE)
            visibility = View.GONE
        }
    }


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
            autoPauseIntervalIndex = it.getInt(KEY_AUTO_PAUSE_INTERVAL_INDEX)
            tagTimeIndex = it.getInt(KEY_TAG_TIME_INDEX)
            currentTagTime = it.getLong(KEY_CURRENT_TAG_TIME)
            it.getLongArray(KEY_TAG_TIMES)?.toList()?.let { times -> setTagTimes(times) }
            it.getLongArray(KEY_AUTO_PAUSE_INTERVALS)?.toList()
                ?.let { times -> setAutoPauseIntervals(times) }
            autoPauseEditEnabled = it.getBoolean(KEY_AUTO_PAUSE_EDIT_ENABLED)
            pauseEditEnabled = it.getBoolean(KEY_PAUSE_EDIT_ENABLED)
            autoPauseTimerRemainingTime = it.getLong(KEY_AUTO_PAUSE_TIMER_REMAINING_TIME)

            if (pauseEditEnabled) {
                binding.pauseEditBtn.background = pencilPauseDrawable
                if (autoPauseEditEnabled) {
                    binding.pauseTimeTV.visibility = View.VISIBLE
                    binding.pauseTimeTV.text =
                        (autoPauseIntervals.elementAt(autoPauseIntervalIndex) / 1000).toString()

                    timer = if (autoPauseTimerRemainingTime == 0L) autoPauseTimer(
                        autoPauseIntervals.elementAt(autoPauseIntervalIndex)
                    ) else autoPauseTimer(
                        autoPauseTimerRemainingTime
                    )
                }
            } else {
                binding.pauseEditBtn.background = pencilPauseInactiveDrawable
            }

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
        playBackSpeedAlertDialogBuilder.setItems(speedList) { _, which ->
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
                    binding.controllerView.apply {
                        TransitionManager.beginDelayedTransition(
                            this, AutoTransition()
                        )
                        visibility = if (isVisible) {
                            View.GONE
                        } else {
                            View.VISIBLE
                        }
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
                if (it.currentPosition >= 5000L) {
                    it.seekTo(it.currentPosition - 5000L)
                }
            }
        }

        binding.forwardBTN.setOnClickListener {
            player?.let {
                if (it.currentPosition <= it.duration - 5000L) {
                    it.seekTo(it.currentPosition + 5000L)
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
            setupPauseEdit()
        }
    }

    private fun setupPauseEdit() {
        player?.currentPosition?.let { ct -> findNearestTagTime(ct, tagTimes) }
        if (!pauseEditEnabled) {
            binding.pauseEditBtn.background = pencilPauseDrawable
            pauseEditEnabled = true
        } else if (pauseEditEnabled) {
            if (autoPauseIntervals.isNotEmpty() && autoPauseIntervalIndex < autoPauseIntervals.size.minus(
                    1
                )
            ) {
                autoPauseIntervalIndex++
                binding.pauseTimeTV.visibility = View.VISIBLE
                binding.pauseTimeTV.text =
                    (autoPauseIntervals.elementAt(autoPauseIntervalIndex) / 1000).toString()
                autoPauseEditEnabled = true
                timer = autoPauseTimer(autoPauseIntervals.elementAt(autoPauseIntervalIndex))

            } else {
                binding.pauseTimeTV.visibility = View.GONE
                autoPauseIntervalIndex = -1
                pauseEditEnabled = false
                autoPauseEditEnabled = false
                binding.pauseEditBtn.background = pencilPauseInactiveDrawable
            }
        }
    }

    private fun autoPauseTimer(duration: Long): CountDownTimer {
        return object : CountDownTimer(duration, 1L) {
            override fun onTick(millisUntilFinished: Long) {
                autoPauseTimerRemainingTime = millisUntilFinished
                val progress =
                    (millisUntilFinished / autoPauseIntervals.elementAt(autoPauseIntervalIndex)
                        .toDouble()) * 100

                binding.pauseProgressBar.progress = progress.toInt()

                if (!binding.pauseProgressBar.isVisible) binding.pauseProgressBar.visibility =
                    View.VISIBLE
                player?.playWhenReady = false
                changePlayPauseIcon()
            }

            override fun onFinish() {
                binding.pauseProgressBar.progress = 0
                if (binding.pauseProgressBar.isVisible) binding.pauseProgressBar.visibility =
                    View.GONE

                player?.playWhenReady = true
                changePlayPauseIcon()
                if (autoPauseTimerRemainingTime < 100L) {
                    autoPauseTimerRemainingTime = 0L
                    timer = autoPauseTimer(autoPauseIntervals.elementAt(autoPauseIntervalIndex))
                }

                handler?.post(autoPauseRunnable)
            }
        }
    }

    private fun playPause() {
        player?.let {
            it.playWhenReady = !it.playWhenReady
            changePlayPauseIcon()
        }
    }

    private fun changePlayPauseIcon() {
        player?.let {
            binding.playPauseBtn.apply {
                if (it.playWhenReady) {
                    setBackgroundResource(R.drawable.baseline_pause_24)
                } else {
                    setBackgroundResource(R.drawable.baseline_play_arrow_24)
                }
            }
        }
    }

    private fun releasePlayer() {
        player?.let {
            updateStartPosition()
            it.release()
            player = null
            binding.playerView.player = null
        }
        timer?.cancel()
        handler?.removeCallbacks(updateProgressAction)
        handler?.removeCallbacks(autoPauseRunnable)
    }

    private fun updateStartPosition() {
        player?.let {
            startAutoPlay = it.playWhenReady
            startPosition = max(0, it.currentPosition)
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
        outState.putLongArray(KEY_TAG_TIMES, tagTimes.toLongArray())
        outState.putLongArray(KEY_AUTO_PAUSE_INTERVALS, autoPauseIntervals.toLongArray())
        outState.putInt(KEY_TAG_TIME_INDEX, tagTimeIndex)
        outState.putInt(KEY_AUTO_PAUSE_INTERVAL_INDEX, autoPauseIntervalIndex)
        outState.putLong(KEY_CURRENT_TAG_TIME, currentTagTime)
        outState.putBoolean(KEY_AUTO_PAUSE_EDIT_ENABLED, autoPauseEditEnabled)
        outState.putBoolean(KEY_PAUSE_EDIT_ENABLED, pauseEditEnabled)
        outState.putLong(KEY_AUTO_PAUSE_TIMER_REMAINING_TIME, autoPauseTimerRemainingTime)
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
                    binding.timeBar.setPosition(it.currentPosition)
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
                handler?.post(autoPauseRunnable)
            } else {
                handler?.removeCallbacks(updateProgressAction)
                handler?.removeCallbacks(autoPauseRunnable)
            }

            if (playbackState == Player.STATE_ENDED) {
                player?.playWhenReady = false
                player?.seekTo(0L)
                if (tagTimes.isNotEmpty()) {
                    tagTimeIndex = 0
                    currentTagTime = tagTimes.elementAt(0)
                }
                handler?.removeCallbacks(autoPauseRunnable)

                isTagTimeVisited.forEach { (t, _) ->
                    isTagTimeVisited[t] = false
                }
            }

            changePlayPauseIcon()
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
            changePlayPauseIcon()
        }

        override fun onScrubMove(timeBar: TimeBar, position: Long) {
            timeBar.setPosition(position)
        }

        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
            isScrubbing = false
            if (!canceled) {
                player?.seekTo(position)
            }
            findNearestTagTime(position, tagTimes)

            isTagTimeVisited.forEach { (t, _) ->
                if (t >= currentTagTime) {
                    isTagTimeVisited[t] = false
                }
            }

            player?.playWhenReady = true
            handler?.post(updateProgressAction)
            changePlayPauseIcon()
        }
    }

    private fun findNearestTagTime(currentTime: Long, tagTimes: TreeSet<Long>) {
        var smallestDifference = Long.MAX_VALUE
        tagTimes.forEachIndexed { index, tagTime ->
            val difference = abs(currentTime - tagTime)
            if (difference < smallestDifference) {
                smallestDifference = difference
                tagTimeIndex = index
                currentTagTime = this.tagTimes.elementAt(tagTimeIndex)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        requestLayout()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    val autoPauseRunnable = object : Runnable {
        override fun run() {

            if (player == null)
                return

            val bufferTime = currentTagTime - (50L * speedMap[playBackSpeed]!!).toLong()

            if (pauseEditEnabled && isTagTimeVisited[currentTagTime] == false && (player?.currentPosition!! in bufferTime..currentTagTime)
            ) {
                isTagTimeVisited[currentTagTime] = true
                player?.playWhenReady = false
                player?.seekTo(currentTagTime)
                player?.currentPosition?.let { ct ->
                    binding.timeBar.setPosition(ct)
                    binding.position.text = getPlayerTime(ct)
                }
                changePlayPauseIcon()
                currentTagTime =
                    if (tagTimes.isNotEmpty() && tagTimeIndex < (tagTimes.size.minus(1))) {
                        tagTimeIndex++
                        tagTimes.elementAt(tagTimeIndex)
                    } else {
                        Long.MAX_VALUE
                    }

                if (autoPauseEditEnabled) {
                    timer?.start()
                } else {
                    handler?.post(this)
                }
            } else {
                handler?.post(this)
            }
        }
    }


}