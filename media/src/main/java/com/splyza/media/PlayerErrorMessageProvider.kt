package com.splyza.media

import android.util.Pair
import androidx.annotation.OptIn
import androidx.media3.common.ErrorMessageProvider
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

@OptIn(markerClass = [UnstableApi::class])
class PlayerErrorMessageProvider : ErrorMessageProvider<PlaybackException> {

    override fun getErrorMessage(e: PlaybackException): Pair<Int, String> {
        var errorString = "Playback failed"
        val cause = e.cause
        if (cause is MediaCodecRenderer.DecoderInitializationException) {
            // Special case for decoder initialization failures.
            errorString = if (cause.codecInfo == null) {
                if (cause.cause is MediaCodecUtil.DecoderQueryException) {
                    "Unable to query device decoders"
                } else if (cause.secureDecoderRequired) {
                    "This device does not provide a secure decoder for " + cause.mimeType
                } else {
                    "This device does not provide a decoder for " + cause.mimeType
                }
            } else {
                "Unable to instantiate decoder " + cause.codecInfo!!.name
            }
        }
        return Pair.create(0, errorString)
    }
}