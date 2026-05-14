package com.example.linter.data.remote

import com.example.linter.domain.model.TextTranslator
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class MLKitTranslator : TextTranslator {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> {
        val source = when (sourceLang) {
            "fr" -> TranslateLanguage.FRENCH
            else -> TranslateLanguage.ENGLISH
        }
        val target = TranslateLanguage.RUSSIAN
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        val translator = Translation.getClient(options)
        return try {
            translator.downloadModelIfNeeded().await()
            val resultText = translator.translate(text).await()
            Result.success(resultText)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            translator.close()
        }
    }
}