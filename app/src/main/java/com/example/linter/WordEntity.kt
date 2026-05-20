package com.example.linter

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

@Entity
data class WordEntity(
    @Id var id: Long = 0, // ObjectBox требует Long ID
    @Unique @Index val word: String = "", // Уникальный индекс для быстрого поиска слова
    var translation: String? = null
)