package com.splyza.media3

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.ui.PlayerView


class SplyzaVideoPlayer: ConstraintLayout, View.OnClickListener {
    constructor(context: Context) : this(context, null){
        setOnClickListener(this)
    }
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0){
        setOnClickListener(this)
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setOnClickListener(this)
    }

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.activity_player,this,true)
        val playerView = view.findViewById<PlayerView>(R.id.playerView)
        val fullScreenBtn = view.findViewById<ImageButton>(R.id.fullscreen)
        fullScreenBtn.setOnClickListener {
            Log.d("onClick","fullScreenBtn.onClick::${it.id}")
            val activity = context as Activity
            activity.requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }


    }

    override fun onClick(v: View) {
        Log.d("onClick","onClick::${v.id}")
    }


}