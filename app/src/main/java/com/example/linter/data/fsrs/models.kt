package com.example.linter.data.fsrs

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

enum class Rating(val value: Int) {
    Again(1),
    Hard(2),
    Good(3),
    Easy(4)
}

enum class CardPhase(val value: Int) {
    Added(0),
    ReLearning(1),
    Review(2),
}

data class Grade(
    val color: Int,  // просто Int, без аннотации
    val title: String,
    val choice: Rating,
    val durationMillis: Long = 0,
    val interval: Int = 0,
    val txt: String = "0",
    val stability: Double = 0.0,
    val difficulty: Double = 0.0
)

@Entity
data class FlashCard(
    @Id var id: Long = 0,
    var stability: Double = 2.5,
    var difficulty: Double = 2.5,
    var interval: Int = 0,
    var dueDateMillis: Long = System.currentTimeMillis(),
    var reviewCount: Int = 0,
    var lastReviewMillis: Long = System.currentTimeMillis(),
    var phase: Int = 0
)