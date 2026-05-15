package com.example.linter.di

import android.content.Context
import com.example.linter.data.model.AndroidBreakIteratorTokenizer
import com.example.linter.data.remote.MLKitTranslator
import com.example.linter.data.repository.LectureRepositoryImpl
import com.example.linter.data.repository.VocabularyRepositoryImpl
import com.example.linter.domain.model.TextTokenizer
import com.example.linter.domain.model.TextTranslator
import com.example.linter.domain.repository.LectureRepository
import com.example.linter.domain.repository.VocabularyRepository

object AppModule {
    lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    // Services
    val translator: TextTranslator by lazy { MLKitTranslator() }
    val tokenizer: TextTokenizer by lazy { AndroidBreakIteratorTokenizer() }

    // Repositories
    val lectureRepository: LectureRepository by lazy { LectureRepositoryImpl() }
    val vocabularyRepository: VocabularyRepository by lazy { VocabularyRepositoryImpl(translator) }

    // Примечание: Старые UseCase удалены.
    // Теперь ViewModels напрямую обращаются к умному VocabularyRepository.
    val reviewRepository: com.example.linter.domain.repository.ReviewRepository by lazy {
        com.example.linter.data.repository.ReviewRepositoryImpl(vocabularyRepository, tokenizer)
    }
}