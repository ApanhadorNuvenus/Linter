package com.example.linter.presentation.ui.lecturelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linter.di.AppModule
import com.example.linter.domain.model.Lecture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LectureListViewModel : ViewModel() {
    private val repository = AppModule.lectureRepository

    private val _lectures = MutableStateFlow<List<Lecture>>(emptyList())
    val lectures: StateFlow<List<Lecture>> = _lectures.asStateFlow()

    private val reviewRepository = AppModule.reviewRepository
    private val _dueCardsCount = MutableStateFlow(0)
    val dueCardsCount: StateFlow<Int> = _dueCardsCount.asStateFlow()

    fun loadDueCount() {
        viewModelScope.launch { _dueCardsCount.value = reviewRepository.getDueCardsCount() }
    }

    fun loadLectures() {
        viewModelScope.launch {
            _lectures.value = repository.getAllLectures()
        }
    }
}