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
    val id: Long,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val sourceText: String,
    val translatedText: String? = null,
    val mlKitTranslatedText: String? = null,
    val onnxTranslatedText: String? = null
)

enum class TranslationMode(val title: String) {
    YOUTUBE_NATIVE("Перевод YouTube (Лучшее качество)"),
    LOCAL_ML_KIT("Быстрый локальный (Google ML Kit)"),
    ADVANCED_ONNX("Нейросеть OPUS-MT (ONNX Офлайн)")
}

data class VideoPlaybackInfo(
    val videoUrl: String,
    val audioUrl: String,
    val subtitles: List<SubtitleBlock>,
    val hasYoutubeTranslation: Boolean,
    val sourceLang: String,
    val availableQualities: List<String> // НОВОЕ: Список доступных разрешений
)