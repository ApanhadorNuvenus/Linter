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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class VocabularyRepositoryImpl(
    private val translator: TextTranslator
) : VocabularyRepository {

    private val vocabBox get() = ObjectBox.vocabularyBox
    private val cardBox get() = ObjectBox.contextCardBox

    override suspend fun getWordMetas(words: List<String>): Map<String, WordMeta> {
        val uniqueWords = words.map { it.lowercase() }.distinct()
        val result = mutableMapOf<String, WordMeta>()

        for (word in uniqueWords) {
            val vocabItem = vocabBox.query(VocabularyItemEntity_.text.equal(word)).build().findFirst()
            if (vocabItem == null) { result[word] = WordMeta(status = UiWordStatus.BLUE); continue }
            if (vocabItem.isKnown || vocabItem.isIgnored) { result[word] = WordMeta(status = UiWordStatus.TRANSPARENT); continue }

            val activeCard = cardBox.query(ContextCardEntity_.vocabularyItemId.equal(vocabItem.id)).build().findFirst()
            if (activeCard != null) {
                result[word] = WordMeta(
                    status = UiWordStatus.YELLOW, learningStatus = LearningStatus.fromLevel(activeCard.status),
                    translations = MultiTranslation(activeCard.translation, activeCard.translationOnnx, activeCard.translationCloud),
                    contextCardId = activeCard.id
                )
            } else {
                result[word] = WordMeta(status = UiWordStatus.BLUE)
            }
        }
        return result
    }

    override suspend fun getLearningPhrasesMetas(): List<Pair<String, WordMeta>> {
        val phraseCards = cardBox.all
        val result = mutableListOf<Pair<String, WordMeta>>()
        for (card in phraseCards) {
            val vocabItem = vocabBox[card.vocabularyItemId]
            if (vocabItem != null && vocabItem.text.contains(" ") && !vocabItem.isKnown && !vocabItem.isIgnored) {
                result.add(vocabItem.text to WordMeta(
                    status = UiWordStatus.YELLOW, learningStatus = LearningStatus.fromLevel(card.status),
                    translations = MultiTranslation(card.translation, card.translationOnnx, card.translationCloud),
                    contextCardId = card.id
                ))
            }
        }
        return result
    }

    override suspend fun fetchMultiTranslations(wordOrPhrase: String, sourceLang: String): MultiTranslation = coroutineScope {
        // Читаем глобальные настройки отображения
        val prefs = AppModule.context.getSharedPreferences("linter_settings", Context.MODE_PRIVATE)
        val showMl = prefs.getBoolean("pref_show_ml_kit", true)
        val showOnnx = prefs.getBoolean("pref_show_onnx", true)
        val showCloud = prefs.getBoolean("pref_show_cloud", true)

        // Запрашиваем только то, что включил пользователь
        val mlDeferred = if (showMl) async { translator.translate(wordOrPhrase, sourceLang, "ru") } else null
        val onnxDeferred = if (showOnnx) async { AppModule.onnxTranslator.translate(wordOrPhrase, sourceLang, "ru") } else null
        val cloudDeferred = if (showCloud) async { AppModule.cloudTranslator.translate(wordOrPhrase, sourceLang, "ru") } else null

        MultiTranslation(
            mlKit = mlDeferred?.await()?.getOrNull(),
            onnx = onnxDeferred?.await()?.getOrNull(),
            cloud = cloudDeferred?.await()?.getOrNull()
        )
    }

    private fun getOrCreateVocabItem(word: String): VocabularyItemEntity {
        val normalized = word.lowercase()
        return vocabBox.query(VocabularyItemEntity_.text.equal(normalized)).build().findFirst()
            ?: VocabularyItemEntity(text = normalized).also { vocabBox.put(it) }
    }

    override suspend fun markAsKnown(word: String) {
        val item = getOrCreateVocabItem(word)
        item.isKnown = true
        vocabBox.put(item)
    }

    override suspend fun markAsIgnored(word: String) {
        val item = getOrCreateVocabItem(word)
        item.isIgnored = true
        vocabBox.put(item)
    }

    override suspend fun createLearningCard(
        word: String, lectureId: Long, youtubeVideoId: Long,
        contextSentence: String, translations: MultiTranslation, status: LearningStatus
    ) {
        val item = getOrCreateVocabItem(word)
        item.isKnown = false
        item.isIgnored = false
        vocabBox.put(item)

        val card = ContextCardEntity(
            vocabularyItemId = item.id, lectureId = lectureId, youtubeVideoId = youtubeVideoId,
            contextSentence = contextSentence,
            translation = translations.mlKit ?: "", // Защита от null
            translationOnnx = translations.onnx,
            translationCloud = translations.cloud,
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

    override suspend fun updateCardStatus(cardId: Long, newStatus: LearningStatus) {
        val card = cardBox[cardId] ?: return
        card.status = newStatus.level
        cardBox.put(card)
    }

    override suspend fun moveCardToKnown(cardId: Long, word: String) {
        cardBox.remove(cardId)
        markAsKnown(word)
    }
}