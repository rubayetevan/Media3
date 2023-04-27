package com.splyza.media3

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.splyza.media3.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        val videoData = Gson().fromJson<VideoData>(Repository.data, VideoData::class.java)
        binding.videoRV.layoutManager = LinearLayoutManager(this)
        binding.videoRV.adapter = VideoAdapter(this@MainActivity, videoData)
    }
}

