package com.example.linter.data.repository

import com.example.linter.data.local.ObjectBox
import com.example.linter.data.local.entity.ContextCardEntity
import com.example.linter.data.local.entity.ContextCardEntity_
import com.example.linter.data.local.entity.VocabularyItemEntity
import com.example.linter.data.local.entity.VocabularyItemEntity_
import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.UiWordStatus
import com.example.linter.domain.model.WordMeta
import com.example.linter.domain.repository.VocabularyRepository
import com.example.linter.domain.model.TextTranslator

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

            if (vocabItem == null) {
                result[word] = WordMeta(status = UiWordStatus.BLUE)
                continue
            }

            if (vocabItem.isKnown || vocabItem.isIgnored) {
                result[word] = WordMeta(status = UiWordStatus.TRANSPARENT)
                continue
            }

            // Ищем активную карточку изучения
            val activeCard = cardBox.query(ContextCardEntity_.vocabularyItemId.equal(vocabItem.id)).build().findFirst()
            if (activeCard != null) {
                result[word] = WordMeta(
                    status = UiWordStatus.YELLOW,
                    learningStatus = LearningStatus.fromLevel(activeCard.status),
                    translation = activeCard.translation,
                    contextCardId = activeCard.id
                )
            } else {
                result[word] = WordMeta(status = UiWordStatus.BLUE)
            }
        }
        return result
    }

    override suspend fun getLearningPhrasesMetas(): List<Pair<String, WordMeta>> {
        val phraseCards = cardBox.all // В идеале тут нужен Join, но ObjectBox работает с этим в памяти быстро
        val result = mutableListOf<Pair<String, WordMeta>>()

        for (card in phraseCards) {
            val vocabItem = vocabBox[card.vocabularyItemId]
            if (vocabItem != null && vocabItem.text.contains(" ") && !vocabItem.isKnown && !vocabItem.isIgnored) {
                result.add(
                    vocabItem.text to WordMeta(
                        status = UiWordStatus.YELLOW,
                        learningStatus = LearningStatus.fromLevel(card.status),
                        translation = card.translation,
                        contextCardId = card.id
                    )
                )
            }
        }
        return result
    }

    override suspend fun fetchTranslation(wordOrPhrase: String, sourceLang: String): String {
        return translator.translate(wordOrPhrase, sourceLang, "ru").getOrElse { "Ошибка перевода" }
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

    override suspend fun createLearningCard(word: String, lectureId: Long, contextSentence: String, translation: String, status: LearningStatus) {
        val item = getOrCreateVocabItem(word)
        item.isKnown = false
        item.isIgnored = false
        vocabBox.put(item)

        val card = ContextCardEntity(
            vocabularyItemId = item.id,
            lectureId = lectureId,
            contextSentence = contextSentence,
            translation = translation,
            status = status.level
        )
        cardBox.put(card)
        // TODO: Здесь же генерировать FlashCardEntity для FSRS
    }

    override suspend fun updateCardStatus(cardId: Long, newStatus: LearningStatus) {
        val card = cardBox[cardId] ?: return
        card.status = newStatus.level
        cardBox.put(card)
    }

    override suspend fun moveCardToKnown(cardId: Long, word: String) {
        // Архивируем/Удаляем карточку и отмечаем глобально
        cardBox.remove(cardId)
        markAsKnown(word)
    }
}