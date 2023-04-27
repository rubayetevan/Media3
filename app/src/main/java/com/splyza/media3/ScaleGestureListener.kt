package com.splyza.media3

import android.view.ScaleGestureDetector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class ScaleGestureListener(
    private val player: PlayerView
) : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    private var scaleFactor = 0f

    override fun onScale(
        detector: ScaleGestureDetector
    ): Boolean {
        scaleFactor = detector.scaleFactor
        return true
    }

    override fun onScaleBegin(
        detector: ScaleGestureDetector
    ): Boolean {
        return true
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onScaleEnd(detector: ScaleGestureDetector) {
        if (scaleFactor > 1) {
            player.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            player.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }
}