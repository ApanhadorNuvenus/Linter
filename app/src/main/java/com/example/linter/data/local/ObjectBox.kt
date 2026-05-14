package com.example.linter.data.local

import android.content.Context
import com.example.linter.data.MyObjectBox
import io.objectbox.Box
import io.objectbox.BoxStore
import com.example.linter.data.local.entity.FlashCardEntity
import com.example.linter.data.local.entity.WordEntity
import com.example.linter.data.local.entity.LectureEntity

object ObjectBox {
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        store =MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
    }

    inline fun <reified T> boxFor(): Box<T> = store.boxFor(T::class.java)

    // Явные свойства для удобства
    val flashCardBox: Box<FlashCardEntity> get() = store.boxFor(FlashCardEntity::class.java)
    val wordBox: Box<WordEntity> get() = store.boxFor(WordEntity::class.java)
    val lectureBox: Box<LectureEntity> get() = store.boxFor(LectureEntity::class.java)
}