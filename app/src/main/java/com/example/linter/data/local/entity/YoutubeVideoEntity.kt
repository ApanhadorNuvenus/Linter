package com.example.linter.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class YoutubeVideoEntity(
    @Id var id: Long = 0,
    var videoUrl: String = "",
    var title: String = "",
    var thumbnailUrl: String = "",
    var progressMs: Long = 0,
    var durationMs: Long = 0,
    var language: String? = "en" // ИСПРАВЛЕНИЕ: Сделано Nullable для совместимости со старой БД
)