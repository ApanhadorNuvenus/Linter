package com.example.linter.data.repository

import android.content.Context
import com.example.linter.data.local.ObjectBox
import com.example.linter.data.local.entity.ContextCardEntity
import com.example.linter.data.local.entity.ContextCardEntity_
import com.example.linter.data.local.entity.FlashCardEntity_
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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

        // Пакетный запрос: Выбираем все активные SRS-карточки планировщика в один запрос к БД
        val activeCardIds = activeCards.map { it.id }.toLongArray()
        val flashCards = if (activeCardIds.isNotEmpty()) {
            ObjectBox.flashCardBox.query(FlashCardEntity_.contextCardId.oneOf(activeCardIds)).build().find()
        } else {
            emptyList()
        }
        val flashCardMap = flashCards.associateBy { it.contextCardId }

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
                val cardLang = getCardLanguage(activeCard)
                // Проверяем, существует ли физическая запись планировщика в БД для этой карты
                val hasFlashCard = flashCardMap.containsKey(activeCard.id)

                result[word] = WordMeta(
                    status = UiWordStatus.YELLOW,
                    learningStatus = LearningStatus.fromLevel(activeCard.status),
                    translations = MultiTranslation(
                        mlKit = activeCard.translation,
                        onnx = activeCard.translationOnnx,
                        cloud = activeCard.translationCloud,
                        custom = activeCard.translationCustom
                    ),
                    contextCardId = activeCard.id,
                    language = cardLang,
                    hasFlashCard = hasFlashCard // Запись статуса РП
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

        // Пакетный запрос SRS планировщика для словосочетаний
        val activeCardIds = phraseCards.map { it.id }.toLongArray()
        val flashCards = if (activeCardIds.isNotEmpty()) {
            ObjectBox.flashCardBox.query(FlashCardEntity_.contextCardId.oneOf(activeCardIds)).build().find()
        } else {
            emptyList()
        }
        val flashCardMap = flashCards.associateBy { it.contextCardId }

        val result = mutableListOf<Pair<String, WordMeta>>()
        for (card in phraseCards) {
            val vocabItem = vocabMap[card.vocabularyItemId]
            if (vocabItem != null && vocabItem.text.contains(" ") && !vocabItem.isKnown && !vocabItem.isIgnored) {
                val cardLang = getCardLanguage(card)
                val hasFlashCard = flashCardMap.containsKey(card.id)

                result.add(
                    vocabItem.text to WordMeta(
                        status = UiWordStatus.YELLOW,
                        learningStatus = LearningStatus.fromLevel(card.status),
                        translations = MultiTranslation(
                            mlKit = card.translation,
                            onnx = card.translationOnnx,
                            cloud = card.translationCloud,
                            custom = card.translationCustom
                        ),
                        contextCardId = card.id,
                        language = cardLang,
                        hasFlashCard = hasFlashCard
                    )
                )
            }
        }
        result
    }

    override suspend fun fetchMultiTranslations(wordOrPhrase: String, sourceLang: String): Flow<MultiTranslation> = channelFlow {
        val prefs = AppModule.context.getSharedPreferences("linter_settings", Context.MODE_PRIVATE)
        val showMl = prefs.getBoolean("pref_show_ml_kit", true)
        val showOnnx = prefs.getBoolean("pref_show_onnx", true)
        val showCloud = prefs.getBoolean("pref_show_cloud", true)

        val isOnnxLoaded = if (showOnnx) AppModule.onnxTranslator.isModelLoaded(sourceLang) else true

        var currentTranslations = MultiTranslation()

        if (!isOnnxLoaded) {
            // СЦЕНАРИЙ 1. ХОЛОДНЫЙ СТАРТ: Сразу пускаем пустую структуру со скелетонами
            send(currentTranslations)

            if (showMl) {
                launch {
                    val res = translator.translate(wordOrPhrase, sourceLang, "ru").getOrNull()
                    currentTranslations = currentTranslations.copy(mlKit = res ?: "Ошибка перевода")
                    send(currentTranslations)
                }
            }
            if (showOnnx) {
                launch {
                    val res = AppModule.onnxTranslator.translate(wordOrPhrase, sourceLang, "ru").getOrNull()
                    currentTranslations = currentTranslations.copy(onnx = res ?: "Ошибка перевода")
                    send(currentTranslations)
                }
            }
            if (showCloud) {
                launch {
                    val res = AppModule.cloudTranslator.translate(wordOrPhrase, sourceLang, "ru").getOrNull()
                    currentTranslations = currentTranslations.copy(cloud = res ?: "Ошибка перевода")
                    send(currentTranslations)
                }
            }
        } else {
            // СЦЕНАРИЙ 2. ГОРЯЧИЙ СТАРТ: Google ML Kit транслируется за 30 мс, а ONNX плавно догружается
            coroutineScope {
                val mlDeferred = if (showMl) async { translator.translate(wordOrPhrase, sourceLang, "ru").getOrNull() } else null
                val onnxDeferred = if (showOnnx) async { AppModule.onnxTranslator.translate(wordOrPhrase, sourceLang, "ru").getOrNull() } else null

                if (showCloud) {
                    launch {
                        val res = AppModule.cloudTranslator.translate(wordOrPhrase, sourceLang, "ru").getOrNull()
                        currentTranslations = currentTranslations.copy(cloud = res ?: "Ошибка перевода")
                        send(currentTranslations)
                    }
                }

                // Ждем супер-быстрый Google ML Kit и СРАЗУ открываем окно (попап) без задержки
                val mlResult = mlDeferred?.await()
                if (showMl && mlResult != null) {
                    currentTranslations = currentTranslations.copy(mlKit = mlResult)
                    send(currentTranslations)
                }

                // ONNX догрузится в фоне следом и плавно обновит свою строчку в попапе
                val onnxResult = onnxDeferred?.await()
                currentTranslations = currentTranslations.copy(
                    onnx = if (showOnnx) (onnxResult ?: "Ошибка перевода") else null
                )
                send(currentTranslations)
            }
        }
    }

    private suspend fun getOrCreateVocabItem(word: String): VocabularyItemEntity = withContext(Dispatchers.IO) {
        val normalized = word.lowercase()
        vocabBox.query(VocabularyItemEntity_.text.equal(normalized)).build().findFirst()
            ?: VocabularyItemEntity(text = normalized).also { vocabBox.put(it) }
    }

    override suspend fun markAsKnown(
        word: String,
        translations: MultiTranslation?,
        contextSentence: String,
        lectureId: Long,
        youtubeVideoId: Long
    ) {
        withContext(Dispatchers.IO) {
            val item = getOrCreateVocabItem(word)
            item.isKnown = true
            vocabBox.put(item)

            // Если предоставлен перевод (слово помечается как известное с самого начала),
            // сохраняем ContextCardEntity с переводами, но НЕ создаем FlashCardEntity!
            if (translations != null) {
                val card = ContextCardEntity(
                    vocabularyItemId = item.id,
                    lectureId = lectureId,
                    youtubeVideoId = youtubeVideoId,
                    contextSentence = contextSentence,
                    translation = translations.mlKit ?: "",
                    translationOnnx = translations.onnx,
                    translationCloud = translations.cloud,
                    translationCustom = translations.custom,
                    status = LearningStatus.NEW.level
                )
                cardBox.put(card)
            }
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

            // ИСПРАВЛЕНИЕ: Если карточка была исключена из РП (FlashCardEntity удалена),
            // но пользователь вручную меняет статус/уровень в попапе, мы автоматически воссоздаем SRS-планировщик!
            val existingFlashCard = ObjectBox.flashCardBox.query(FlashCardEntity_.contextCardId.equal(cardId)).build().findFirst()
            if (existingFlashCard == null) {
                val newFlashCard = com.example.linter.data.local.entity.FlashCardEntity(
                    contextCardId = cardId,
                    stability = 0.0,
                    difficulty = 0.0,
                    interval = 0,
                    dueDateMillis = System.currentTimeMillis(),
                    reviewCount = 0,
                    lastReviewMillis = System.currentTimeMillis(),
                    phase = com.example.linter.data.fsrs.CardPhase.Added.value
                )
                ObjectBox.flashCardBox.put(newFlashCard)
            }
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


    // ИСПРАВЛЕНИЕ: Метод для точного считывания языка контекстной карты из БД
    private fun getCardLanguage(card: ContextCardEntity): String {
        if (card.lectureId > 0L) {
            val lecture = ObjectBox.lectureBox[card.lectureId]
            if (lecture != null) return lecture.language
        }
        if (card.youtubeVideoId > 0L) {
            val video = ObjectBox.store.boxFor(com.example.linter.data.local.entity.YoutubeVideoEntity::class.java)[card.youtubeVideoId]
            if (video != null) {
                // ИСПРАВЛЕНИЕ: Безопасное разворачивание Nullable-поля старых записей
                return video.language ?: "en"
            }
        }
        return "en"
    }

}