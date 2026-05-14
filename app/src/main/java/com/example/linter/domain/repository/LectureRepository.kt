package com.example.linter.domain.repository

import com.example.linter.domain.model.Lecture

interface LectureRepository {
    suspend fun createLecture(title: String, text: String, language: String): Lecture
    suspend fun getAllLectures(): List<Lecture>
    suspend fun getLectureById(id: Long): Lecture?
}