package com.example.linter.domain.model

enum class LearningStatus(val level: Int) {
    NEW(1),
    RECOGNIZED(2),
    FAMILIAR(3),
    LEARNED(4);

    companion object {
        fun fromLevel(level: Int): LearningStatus = entries.find { it.level == level } ?: NEW
    }
}

enum class UiWordStatus {
    BLUE,       // Новое слово
    YELLOW,     // Учим (есть ContextCard)
    TRANSPARENT // Знаем или Игнорируем
}

data class WordMeta(
    val status: UiWordStatus,
    val learningStatus: LearningStatus? = null,
    val translation: String? = null,
    val contextCardId: Long? = null
)