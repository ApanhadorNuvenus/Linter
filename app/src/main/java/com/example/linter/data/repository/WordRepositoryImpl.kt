package com.example.linter.data.repository

import com.example.linter.data.local.ObjectBox
import com.example.linter.data.local.entity.WordEntity
import com.example.linter.data.local.entity.WordEntity_
import com.example.linter.data.local.mapper.toDomain
import com.example.linter.domain.model.Familiarity
import com.example.linter.domain.model.Word
import com.example.linter.domain.repository.WordRepository
import com.example.linter.domain.model.TextTranslator

class WordRepositoryImpl(
    private val translator: TextTranslator
) : WordRepository {

    private val wordBox get() = ObjectBox.wordBox

    override suspend fun addWordsIfNotExist(words: List<String>) {
        val uniqueWords = words.map { it.lowercase() }.distinct()
        val existingEntities = mutableListOf<WordEntity>()

        for (word in uniqueWords) {
            wordBox.query(WordEntity_.word.equal(word)).build().findFirst()?.let {
                existingEntities.add(it)
            }
        }

        val existingSet = existingEntities.map { it.word }.toSet()
        val newEntities = uniqueWords.filter { it !in existingSet }.map { WordEntity(word = it) }

        if (newEntities.isNotEmpty()) {
            wordBox.put(newEntities)
        }
    }

    override suspend fun getTranslation(word: String, sourceLang: String, targetLang: String, saveToDb: Boolean): String {
        val normalized = word.lowercase()
        val entity = wordBox.query(WordEntity_.word.equal(normalized)).build().findFirst()
        if (entity != null && entity.translation != null) {
            return entity.translation!!
        }
        val result = translator.translate(word, sourceLang, targetLang)
        val translation = result.getOrElse { "Ошибка" }

        if (saveToDb) {
            val toSave = entity ?: WordEntity(word = normalized)
            toSave.translation = translation
            wordBox.put(toSave)
        }
        return translation
    }

    override suspend fun getWord(word: String): Word? {
        val entity = wordBox.query(WordEntity_.word.equal(word.lowercase())).build().findFirst()
        return entity?.toDomain()
    }

    override suspend fun updateFamiliarity(word: String, familiarity: Familiarity) {
        val entity = wordBox.query(WordEntity_.word.equal(word.lowercase())).build().findFirst()
        if (entity != null) {
            entity.familiarity = familiarity.value
            wordBox.put(entity)
        } else {
            wordBox.put(WordEntity(word = word.lowercase(), familiarity = familiarity.value))
        }
    }

    override suspend fun getFamiliarityMap(words: List<String>): Map<String, Familiarity> {
        val unique = words.map { it.lowercase() }.distinct()
        val result = mutableMapOf<String, Familiarity>()
        for (word in unique) {
            val entity = wordBox.query(WordEntity_.word.equal(word)).build().findFirst()
            entity?.let {
                result[it.word] = Familiarity.fromValue(it.familiarity)
            }
        }
        return result
    }

    override suspend fun getTranslationMap(words: List<String>, sourceLang: String, targetLang: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (word in words) {
            result[word] = getTranslation(word, sourceLang, targetLang, saveToDb = true)
        }
        return result
    }

    override suspend fun getAllKnownPhrases(): List<Word> {
        return wordBox.query(WordEntity_.word.contains(" "))
            .build()
            .find()
            .filter { Familiarity.fromValue(it.familiarity) != Familiarity.UNKNOWN }
            .map { it.toDomain() }
    }
}