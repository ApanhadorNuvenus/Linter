package com.example.linter.data.remote

import com.example.linter.domain.model.TextTranslator
import kotlinx.coroutines.delay

class CloudTranslatorStub : TextTranslator {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> {
        delay(300) // Имитация сетевой задержки
        return Result.success("☁️ Облачный перевод API (Заглушка для $sourceLang-$targetLang)")
    }
}