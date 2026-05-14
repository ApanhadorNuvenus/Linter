package com.example.linter.domain.model

data class Word(
    val id: Long,
    val text: String,
    val translation: String?,
    val familiarity: Familiarity
)