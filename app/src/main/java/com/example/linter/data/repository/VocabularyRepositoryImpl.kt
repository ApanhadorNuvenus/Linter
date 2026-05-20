package com.example.linter.data.repository

import android.content.Context
import com.example.linter.data.local.ObjectBox
import com.example.linter.data.local.entity.ContextCardEntity
import com.example.linter.data.local.entity.ContextCardEntity_
import com.example.linter.data.local.entity.VocabularyItemEntity
import com.example.linter.data.local.entity.VocabularyItemEntity_
import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.UiWordStatus
import com.example.linter.domain.model.WordMeta
import com.example.linter.domain.model.MultiTranslation
import com.example.linter.domain.repository.VocabularyRepository
import com.example.linter.domain.model.TextTranslator
import com.example.linter.di.AppModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class VocabularyRepositoryImpl(
    private val translator: TextTranslator
) : VocabularyRepository {

    private val vocabBox get() = ObjectBox.vocabularyBox
    private val cardBox get() = ObjectBox.contextCardBox

    override suspend fun getWordMetas(words: List<String>): Map<String, WordMeta> = withContext(Dispatchers.IO) {
        val uniqueWords = words.map { it.lowercase() }.distinct()
        if (uniqueWords.isEmpty()) return@withContext emptyMap()

        val result = mutableMapOf<String, WordMeta>()

        val vocabItems = vocabBox.query(VocabularyItemEntity_.text.oneOf(uniqueWords.toTypedArray())).build().find()
        val vocabMap = vocabItems.associateBy { it.text }

        val vocabItemIds = vocabItems.map { it.id }.toLongArray()
        val activeCards = if (vocabItemIds.isNotEmpty()) {
            cardBox.query(ContextCardEntity_.vocabularyItemId.oneOf(vocabItemIds)).build().find()
        } else {
            emptyList()
        }
        val cardMap = activeCards.associateBy { it.vocabularyItemId }

        for (word in uniqueWords) {
            val vocabItem = vocabMap[word]
            if (vocabItem == null) {
                result[word] = WordMeta(status = UiWordStatus.BLUE)
                continue
            }
            if (vocabItem.isKnown || vocabItem.isIgnored) {
                result[word] = WordMeta(status = UiWordStatus.TRANSPARENT)
                continue
            }

            val activeCard = cardMap[vocabItem.id]
            if (activeCard != null) {
                result[word] = WordMeta(
                    status = UiWordStatus.YELLOW,
                    learningStatus = LearningStatus.fromLevel(activeCard.status),
                    translations = MultiTranslation(
                        mlKit = activeCard.translation,
                        onnx = activeCard.translationOnnx,
                        cloud = activeCard.translationCloud,
                        custom = activeCard.translationCustom // Чтение кастомного перевода
                    ),
                    contextCardId = activeCard.id
                )
            } else {
                result[word] = WordMeta(status = UiWordStatus.BLUE)
            }
        }
        result
    }

    override suspend fun getLearningPhrasesMetas(): List<Pair<String, WordMeta>> = withContext(Dispatchers.IO) {
        val phraseCards = cardBox.all
        if (phraseCards.isEmpty()) return@withContext emptyList()

        val cardItemIds = phraseCards.map { it.vocabularyItemId }.toLongArray()
        val vocabItems = vocabBox.query(VocabularyItemEntity_.id.oneOf(cardItemIds)).build().find()
        val vocabMap = vocabItems.associateBy { it.id }

        val result = mutableListOf<Pair<String, WordMeta>>()
        for (card in phraseCards) {
            val vocabItem = vocabMap[card.vocabularyItemId]
            if (vocabItem != null && vocabItem.text.contains(" ") && !vocabItem.isKnown && !vocabItem.isIgnored) {
                result.add(
                    vocabItem.text to WordMeta(
                        status = UiWordStatus.YELLOW,
                        learningStatus = LearningStatus.fromLevel(card.status),
                        translations = MultiTranslation(
                            mlKit = card.translation,
                            onnx = card.translationOnnx,
                            cloud = card.translationCloud,
                            custom = card.translationCustom // Чтение кастомного перевода
                        ),
                        contextCardId = card.id
                    )
                )
            }
        }
        result
    }

    override suspend fun fetchMultiTranslations(wordOrPhrase: String, sourceLang: String): MultiTranslation = coroutineScope {
        val prefs = AppModule.context.getSharedPreferences("linter_settings", Context.MODE_PRIVATE)
        val showMl = prefs.getBoolean("pref_show_ml_kit", true)
        val showOnnx = prefs.getBoolean("pref_show_onnx", true)
        val showCloud = prefs.getBoolean("pref_show_cloud", true)

        val mlDeferred = if (showMl) async { translator.translate(wordOrPhrase, sourceLang, "ru") } else null
        val onnxDeferred = if (showOnnx) async { AppModule.onnxTranslator.translate(wordOrPhrase, sourceLang, "ru") } else null
        val cloudDeferred = if (showCloud) async { AppModule.cloudTranslator.translate(wordOrPhrase, sourceLang, "ru") } else null

        MultiTranslation(
            mlKit = mlDeferred?.await()?.getOrNull(),
            onnx = onnxDeferred?.await()?.getOrNull(),
            cloud = cloudDeferred?.await()?.getOrNull(),
            custom = null
        )
    }

    private suspend fun getOrCreateVocabItem(word: String): VocabularyItemEntity = withContext(Dispatchers.IO) {
        val normalized = word.lowercase()
        vocabBox.query(VocabularyItemEntity_.text.equal(normalized)).build().findFirst()
            ?: VocabularyItemEntity(text = normalized).also { vocabBox.put(it) }
    }

    override suspend fun markAsKnown(word: String) {
        withContext(Dispatchers.IO) {
            val item = getOrCreateVocabItem(word)
            item.isKnown = true
            vocabBox.put(item)
        }
    }

    override suspend fun markAsIgnored(word: String) {
        withContext(Dispatchers.IO) {
            val item = getOrCreateVocabItem(word)
            item.isIgnored = true
            vocabBox.put(item)
        }
    }

    override suspend fun createLearningCard(
        word: String, lectureId: Long, youtubeVideoId: Long,
        contextSentence: String, translations: MultiTranslation, status: LearningStatus
    ) {
        withContext(Dispatchers.IO) {
            val item = getOrCreateVocabItem(word)
            item.isKnown = false
            item.isIgnored = false
            vocabBox.put(item)

            val card = ContextCardEntity(
                vocabularyItemId = item.id, lectureId = lectureId, youtubeVideoId = youtubeVideoId,
                contextSentence = contextSentence,
                translation = translations.mlKit ?: "",
                translationOnnx = translations.onnx,
                translationCloud = translations.cloud,
                translationCustom = translations.custom, // Запись кастомного перевода
                status = status.level
            )
            val contextCardId = cardBox.put(card)

            val flashCard = com.example.linter.data.local.entity.FlashCardEntity(
                contextCardId = contextCardId,
                stability = 0.0,
                difficulty = 0.0,
                interval = 0,
                dueDateMillis = System.currentTimeMillis(),
                reviewCount = 0,
                lastReviewMillis = System.currentTimeMillis(),
                phase = com.example.linter.data.fsrs.CardPhase.Added.value
            )
            com.example.linter.data.local.ObjectBox.flashCardBox.put(flashCard)
        }
    }

    override suspend fun updateCardStatus(cardId: Long, newStatus: LearningStatus) {
        withContext(Dispatchers.IO) {
            val card = cardBox[cardId] ?: return@withContext
            card.status = newStatus.level
            cardBox.put(card)
        }
    }

    override suspend fun moveCardToKnown(cardId: Long, word: String) {
        withContext(Dispatchers.IO) {
            cardBox.remove(cardId)
            markAsKnown(word)
        }
    }

    override suspend fun updateCustomTranslation(cardId: Long, customTranslation: String?) {
        withContext(Dispatchers.IO) {
            val card = cardBox[cardId] ?: return@withContext
            card.translationCustom = customTranslation?.trim()?.takeIf { it.isNotBlank() }
            cardBox.put(card)
        }
    }
}