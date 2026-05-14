package com.example.linter.domain.model

data class Token(
    val value: String,
    val startIndex: Int,
    val endIndex: Int,
    val isWord: Boolean
)