package com.example.linter.domain.repository

import com.example.linter.domain.model.SubtitleBlock
import com.example.linter.domain.model.YoutubeVideo
import com.example.linter.domain.model.VideoPlaybackInfo

interface YoutubeRepository {
    suspend fun extractAndSaveVideo(url: String): Result<YoutubeVideo>
    suspend fun getSavedVideos(): List<YoutubeVideo>
    suspend fun getVideoById(id: Long): YoutubeVideo?
    suspend fun updateProgress(id: Long, progressMs: Long)
    suspend fun deleteVideo(id: Long) // НОВОЕ: Каскадное удаление

    suspend fun fetchPlaybackInfo(videoId: Long, url: String): Result<VideoPlaybackInfo>

    suspend fun translateAndSaveBlockLocally(block: SubtitleBlock, sourceLang: String): SubtitleBlock
    suspend fun clearTranslationsForVideo(videoId: Long) // НОВОЕ: Для смены режима
}