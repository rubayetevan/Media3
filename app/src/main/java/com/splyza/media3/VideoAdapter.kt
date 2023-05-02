package com.splyza.media3

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.splyza.media3.databinding.VideoListItemBinding
import com.squareup.picasso.Picasso

class VideoAdapter(private val context: Context, private val videoData: VideoData) :
    RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = VideoListItemBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun getItemCount(): Int {
        if (videoData.categories.isEmpty())
            return 0
        if (videoData.categories[0].videos.isEmpty())
            return 0

        return videoData.categories[0].videos.size
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videoData.categories[0].videos[position]
        holder.binding.titleTV.text = video.title
        holder.binding.subTitleTV.text = video.subtitle
        Picasso.get().load(getThumbUrl(video)).into(holder.binding.thumbIMGV)
        holder.binding.root.setOnClickListener {
            val intent = Intent(context, TestActivity::class.java)
            intent.putExtra(SplyzaVideoPlayer.VIDEO_SOURCE, video.sources[0])
            context.startActivity(intent)
        }
    }

    private fun getThumbUrl(video: Video): String {
        val regex = Regex("^.*\\/\\/.*?\\/.*?(?=\\/)")
        val input = video.sources[0]
        val matchResult = regex.find(input)
        return "${matchResult?.value}/sample/${video.thumb}"
    }

    inner class VideoViewHolder(val binding: VideoListItemBinding) :
        RecyclerView.ViewHolder(binding.root)
}