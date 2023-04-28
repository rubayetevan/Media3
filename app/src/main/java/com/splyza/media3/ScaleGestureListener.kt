package com.splyza.media3

import android.util.Log
import android.view.ScaleGestureDetector
import androidx.media3.ui.PlayerView
import kotlin.math.max
import kotlin.math.min

class ScaleGestureListener(
    private val playerView: PlayerView
) : ScaleGestureDetector.SimpleOnScaleGestureListener() {

    companion object {
        private const val TAG = "ScaleGestureListener"
    }

    private var scaleFactor = 1f

    override fun onScale(
        detector: ScaleGestureDetector
    ): Boolean {
        scaleFactor *= detector.scaleFactor
        scaleFactor = max(0.1f, min(scaleFactor, 10.0f))
        if (scaleFactor > 1) {
            playerView.scaleX = scaleFactor
            playerView.scaleY = scaleFactor
        }
        return true
    }

    override fun onScaleBegin(
        detector: ScaleGestureDetector
    ): Boolean {
        return true
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onScaleEnd(detector: ScaleGestureDetector) {
        Log.d(TAG, "detector.scaleFactor = ${detector.scaleFactor}")
        /*if (scaleFactor > 1) {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }*/

    }
}