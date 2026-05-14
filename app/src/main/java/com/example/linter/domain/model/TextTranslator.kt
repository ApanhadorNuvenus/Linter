package com.example.linter.domain.model

interface TextTranslator {
    suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String>
}