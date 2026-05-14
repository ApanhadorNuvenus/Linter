package com.example.linter.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class FlashCardEntity(
    @Id var id: Long = 0,
    var wordId: Long = 0,       // связь с WordEntity
    var stability: Double = 2.5,
    var difficulty: Double = 2.5,
    var interval: Int = 0,
    var dueDateMillis: Long = System.currentTimeMillis(),
    var reviewCount: Int = 0,
    var lastReviewMillis: Long = System.currentTimeMillis(),
    var phase: Int = 0
)