package com.example.linter.domain.model

interface TextTokenizer {
    fun tokenize(text: String): List<Token>
}