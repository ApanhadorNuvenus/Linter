package com.example.linter.domain.repository

import com.example.linter.domain.model.SubtitleBlock
import com.example.linter.domain.model.YoutubeVideo
import com.example.linter.domain.model.VideoPlaybackInfo
import com.example.linter.domain.model.TranslationMode

interface YoutubeRepository {
    suspend fun extractAndSaveVideo(url: String): Result<YoutubeVideo>
    suspend fun getSavedVideos(): List<YoutubeVideo>
    suspend fun getVideoById(id: Long): YoutubeVideo?
    suspend fun updateProgress(id: Long, progressMs: Long)
    suspend fun deleteVideo(id: Long)

    // ИЗМЕНЕНИЕ: Добавлен targetQuality
    suspend fun fetchPlaybackInfo(videoId: Long, url: String, targetQuality: String = "Auto"): Result<VideoPlaybackInfo>

    suspend fun translateAndSaveBlockLocally(block: SubtitleBlock, sourceLang: String, mode: TranslationMode): SubtitleBlock

    // НОВЫЕ МЕТОДЫ:
    suspend fun deleteSubtitlesForVideo(videoId: Long) // Жесткая очистка кэша
    fun getDefaultQuality(): String
    fun setDefaultQuality(quality: String)
}