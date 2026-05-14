package com.example.linter.data.local

import android.content.Context
import com.example.linter.data.MyObjectBox
import io.objectbox.Box
import io.objectbox.BoxStore
import com.example.linter.data.local.entity.FlashCardEntity
import com.example.linter.data.local.entity.LectureEntity
import com.example.linter.data.local.entity.VocabularyItemEntity
import com.example.linter.data.local.entity.ContextCardEntity

object ObjectBox {
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
    }

    val flashCardBox: Box<FlashCardEntity> get() = store.boxFor(FlashCardEntity::class.java)
    val lectureBox: Box<LectureEntity> get() = store.boxFor(LectureEntity::class.java)
    val vocabularyBox: Box<VocabularyItemEntity> get() = store.boxFor(VocabularyItemEntity::class.java)
    val contextCardBox: Box<ContextCardEntity> get() = store.boxFor(ContextCardEntity::class.java)
}