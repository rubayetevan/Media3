package com.splyza.media3

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.splyza.media3.databinding.ActivityTestBinding

class TestActivity : AppCompatActivity() {

    private fun initImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    }


    private lateinit var binding: ActivityTestBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initImmersiveMode()
        binding = ActivityTestBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        binding.videoPlayer.onCreate(savedInstanceState, intent)
    }

    override fun onStart() {
        super.onStart()
        binding.videoPlayer.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.videoPlayer.onResume()
    }

    override fun onStop() {
        super.onStop()
        binding.videoPlayer.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.videoPlayer.onSaveInstanceState(outState)
    }

}