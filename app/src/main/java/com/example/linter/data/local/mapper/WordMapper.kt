package com.example.linter.data.local.mapper

import com.example.linter.data.local.entity.WordEntity
import com.example.linter.domain.model.Familiarity
import com.example.linter.domain.model.Word

fun WordEntity.toDomain(): Word = Word(
    id = id,
    text = word,
    translation = translation,
    familiarity = Familiarity.fromValue(familiarity)
)

fun Word.toEntity(): WordEntity = WordEntity(
    id = id,
    word = text,
    translation = translation,
    familiarity = familiarity.value
)