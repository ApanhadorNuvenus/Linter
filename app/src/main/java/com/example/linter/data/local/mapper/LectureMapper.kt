package com.example.linter.data.local.mapper

import com.example.linter.data.local.entity.LectureEntity
import com.example.linter.domain.model.Lecture

fun LectureEntity.toDomain(): Lecture = Lecture(
    id = id,
    title = title,
    text = text,
    language = language
)

fun Lecture.toEntity(): LectureEntity = LectureEntity(
    id = id,
    title = title,
    text = text,
    language = language
)