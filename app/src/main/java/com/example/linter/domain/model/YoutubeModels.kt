package com.example.linter.domain.model

data class YoutubeVideo(
    val id: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val progressMs: Long,
    val durationMs: Long
)

data class SubtitleBlock(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val sourceText: String,
    val translatedText: String? = null // Перевод может быть загружен позже локально
)

enum class TranslationMode(val title: String) {
    YOUTUBE_NATIVE("Перевод YouTube (Лучшее качество)"),
    LOCAL_ML_KIT("Локальный перевод (ML Kit)")
}

data class VideoPlaybackInfo(
    val videoUrl: String,
    val audioUrl: String,
    val subtitles: List<SubtitleBlock>,
    val hasYoutubeTranslation: Boolean
)