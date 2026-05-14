package com.example.linter.domain.model

enum class Familiarity(val value: Int) {
    UNKNOWN(0),   // Не знакомо
    FAMILIAR(1),  // Знакомо
    LEARNING(2),  // Учу
    IGNORED(3);   // Игнорировать

    companion object {
        fun fromValue(value: Int): Familiarity =
            entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}