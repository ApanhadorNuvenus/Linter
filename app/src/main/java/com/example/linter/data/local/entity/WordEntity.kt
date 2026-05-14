package com.example.linter.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

@Entity
data class WordEntity(
    @Id var id: Long = 0,
    @Unique @Index var word: String = "",
    var translation: String? = null,
    var familiarity: Int = 0   // Familiarity.UNKNOWN.value
)