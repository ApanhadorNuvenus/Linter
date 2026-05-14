package com.example.linter.data.local.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class LectureEntity(
    @Id var id: Long = 0,
    var title: String = "",
    var text: String = "",
    var language: String = "en"
)