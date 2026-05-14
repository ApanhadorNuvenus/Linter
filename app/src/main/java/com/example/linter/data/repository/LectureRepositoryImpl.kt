package com.example.linter.data.repository

import com.example.linter.data.local.ObjectBox
import com.example.linter.data.local.entity.LectureEntity
import com.example.linter.data.local.mapper.toDomain
import com.example.linter.domain.model.Lecture
import com.example.linter.domain.repository.LectureRepository

class LectureRepositoryImpl : LectureRepository {

    private val lectureBox get() = ObjectBox.lectureBox

    override suspend fun createLecture(title: String, text: String, language: String): Lecture {
        val entity = LectureEntity(title = title, text = text, language = language)
        lectureBox.put(entity)
        return entity.toDomain()
    }

    override suspend fun getAllLectures(): List<Lecture> {
        return lectureBox.all.map { it.toDomain() }
    }

    override suspend fun getLectureById(id: Long): Lecture? {
        return lectureBox[id]?.toDomain()
    }
}