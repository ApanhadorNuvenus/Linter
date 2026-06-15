package com.example.linter.di

import android.content.Context
import com.example.linter.data.model.AndroidBreakIteratorTokenizer
import com.example.linter.data.remote.MLKitTranslator
import com.example.linter.data.remote.HelsinkiOnnxTranslator
import com.example.linter.data.remote.CloudTranslatorStub
import com.example.linter.data.repository.LectureRepositoryImpl
import com.example.linter.data.repository.VocabularyRepositoryImpl
import com.example.linter.data.tts.TtsRepositoryImpl
import com.example.linter.data.youtube.NewPipeDownloader
import com.example.linter.data.youtube.YoutubeRepositoryImpl
import com.example.linter.domain.model.TextTokenizer
import com.example.linter.domain.model.TextTranslator
import com.example.linter.domain.repository.LectureRepository
import com.example.linter.domain.repository.TtsRepository
import com.example.linter.domain.repository.VocabularyRepository
import com.example.linter.domain.repository.YoutubeRepository
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

object AppModule {
    lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext

        // Инициализация NewPipeExtractor
        NewPipe.init(NewPipeDownloader.getInstance(), Localization("en", "US"))
    }

    val mlKitTranslator: TextTranslator by lazy { MLKitTranslator() }
    val onnxTranslator: TextTranslator by lazy { HelsinkiOnnxTranslator(context) }
    val cloudTranslator: TextTranslator by lazy { CloudTranslatorStub() }
    val tokenizer: TextTokenizer by lazy { AndroidBreakIteratorTokenizer() }

    // НОВОЕ: Внедрение TtsRepository
    val ttsRepository: TtsRepository by lazy { TtsRepositoryImpl(context) }

    val lectureRepository: LectureRepository by lazy { LectureRepositoryImpl() }
    val vocabularyRepository: VocabularyRepository by lazy { VocabularyRepositoryImpl(mlKitTranslator) }

    val reviewRepository: com.example.linter.domain.repository.ReviewRepository by lazy {
        com.example.linter.data.repository.ReviewRepositoryImpl(vocabularyRepository, tokenizer)
    }

    val youtubeRepository: YoutubeRepository by lazy {
        YoutubeRepositoryImpl(mlKitTranslator, onnxTranslator, context)
    }
}