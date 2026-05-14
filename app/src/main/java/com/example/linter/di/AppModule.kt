package com.example.linter.di

import android.content.Context
import com.example.linter.data.local.ObjectBox
import com.example.linter.data.model.AndroidBreakIteratorTokenizer
import com.example.linter.data.remote.MLKitTranslator
import com.example.linter.data.repository.WordRepositoryImpl
import com.example.linter.data.repository.LectureRepositoryImpl
import com.example.linter.data.repository.FSRSRepositoryImpl
import com.example.linter.domain.repository.WordRepository
import com.example.linter.domain.repository.LectureRepository
import com.example.linter.domain.repository.FSRSRepository
import com.example.linter.domain.model.TextTokenizer
import com.example.linter.domain.model.TextTranslator
import com.example.linter.domain.usecase.ProcessLectureTextUseCase
import com.example.linter.domain.usecase.GetLectureWithWordInfoUseCase
import com.example.linter.domain.usecase.UpdateWordFamiliarityUseCase

object AppModule {
    lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    // Repositories
    val wordRepository: WordRepository by lazy {
        WordRepositoryImpl(translator = translator)
    }
    val lectureRepository: LectureRepository by lazy {
        LectureRepositoryImpl()
    }
    val fsrsRepository: FSRSRepository by lazy {
        FSRSRepositoryImpl()
    }

    // Services
    val translator: TextTranslator by lazy { MLKitTranslator() }
    val tokenizer: TextTokenizer by lazy { AndroidBreakIteratorTokenizer() }

    // Use cases
    val processLectureTextUseCase by lazy {
        ProcessLectureTextUseCase(wordRepository, tokenizer)
    }
    val getLectureWithWordInfoUseCase by lazy {
        GetLectureWithWordInfoUseCase(wordRepository)
    }
    val updateWordFamiliarityUseCase by lazy {
        UpdateWordFamiliarityUseCase(wordRepository, fsrsRepository)
    }
}