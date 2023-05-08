package com.splyza.media


import android.view.ScaleGestureDetector
import androidx.media3.ui.PlayerView
import kotlin.math.max
import kotlin.math.min

class ScaleGestureListener(
    private val playerView: PlayerView
) : ScaleGestureDetector.SimpleOnScaleGestureListener() {

    companion object {
        private const val TAG = "ScaleGestureListener"
        private const val MIN_SCALE_FACTOR = 0.1f
        private const val MAX_SCALE_FACTOR = 10f
        var scaleFactor = 1f
            private set
    }

    private val initialScaleX = playerView.scaleX
    private val initialScaleY = playerView.scaleY

    override fun onScale(
        detector: ScaleGestureDetector
    ): Boolean {
        scaleFactor *= detector.scaleFactor
        scaleFactor = max(MIN_SCALE_FACTOR, min(scaleFactor, MAX_SCALE_FACTOR))
        setScaleFactor(scaleFactor)
        return true
    }

    fun setScaleFactor(factor: Float?) {
        factor?.let {
            scaleFactor = it
            if (scaleFactor > 1) {
                playerView.scaleX = initialScaleX * scaleFactor
                playerView.scaleY = initialScaleY * scaleFactor
            }
        }
    }

}