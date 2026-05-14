package com.example.linter.domain.model

data class Lecture(
    val id: Long,
    val title: String,
    val text: String,
    val language: String   // "en" или "fr"
)