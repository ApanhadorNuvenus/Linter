package com.example.linter

import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTest {

    private lateinit var store: BoxStore
    private lateinit var wordBox: Box<WordEntity>

    @Before
    fun setup() {
        val tempDir = File.createTempFile("objectbox-test", "")
        tempDir.delete()
        tempDir.mkdirs()

        store = MyObjectBox.builder()
            .directory(tempDir)
            .build()
        wordBox = store.boxFor(WordEntity::class.java)
    }

    @After
    fun tearDown() {
        if (::store.isInitialized) {
            store.close()
            store.deleteAllFiles()
        }
    }

    @Test
    fun `test caching logic with ObjectBox`() = runTest {
        val translator = FakeTranslator()
        val repo = WordRepositoryImpl(wordBox, translator)

        // 1. Первый запрос (идет в переводчик)
        repo.getTranslation("Run", "en", "ru")
        assertEquals(1, translator.translationCount)

        // 2. Второй запрос того же слова (идет в БД)
        repo.getTranslation("run", "en", "ru")
        assertEquals(1, translator.translationCount)

        // 3. Проверка записи в БД
        val allWords = wordBox.all
        val entity = allWords.firstOrNull { it.word == "run" }
        assertEquals("Перевод_Run", entity?.translation)
    }
}