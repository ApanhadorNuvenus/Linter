package com.example.linter.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class SubtitleBlockEntity(
    @Id var id: Long = 0,
    @Index var youtubeVideoId: Long = 0,
    var startTimeMs: Long = 0,
    var endTimeMs: Long = 0,
    var sourceText: String = "",
    var translatedText: String? = null,      // Хранит перевод YouTube
    var mlKitTranslatedText: String? = null, // Хранит ML Kit
    var onnxTranslatedText: String? = null   // Хранит ONNX
)