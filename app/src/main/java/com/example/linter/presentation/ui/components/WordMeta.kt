package com.example.linter.presentation.ui.components

import com.example.linter.domain.model.Familiarity

data class WordMeta(
    val familiarity: Familiarity,
    val translation: String?
)