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

    // УДАЛИТЕ ИЛИ ЗАКОММЕНТИРУЙТЕ ЭТОТ БЛОК:
    // init {
    //     loadLectures()
    // }

    fun loadLectures() {
        viewModelScope.launch {
            _lectures.value = repository.getAllLectures()
        }
    }
}