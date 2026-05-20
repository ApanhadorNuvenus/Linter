package com.example.linter.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

@Entity
data class VocabularyItemEntity(
    @Id var id: Long = 0,
    @Unique @Index var text: String = "",
    var isKnown: Boolean = false,
    var isIgnored: Boolean = false
)

@Entity
data class ContextCardEntity(
    @Id var id: Long = 0,
    @Index var vocabularyItemId: Long = 0,
    var lectureId: Long = 0,
    var youtubeVideoId: Long = 0,
    var contextSentence: String = "",
    var translation: String = "", // ML Kit (Основной, для совместимости со старой базой)
    var translationOnnx: String? = null,   // НОВОЕ
    var translationCloud: String? = null,  // НОВОЕ
    var status: Int = 1
)