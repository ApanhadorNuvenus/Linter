package com.example.linter.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class SubtitleBlockEntity(
    @Id var id: Long = 0, // Уникальный ID ObjectBox
    @Index var youtubeVideoId: Long = 0, // Связь с видео
    var startTimeMs: Long = 0,
    var endTimeMs: Long = 0,
    var sourceText: String = "",
    var translatedText: String? = null
)