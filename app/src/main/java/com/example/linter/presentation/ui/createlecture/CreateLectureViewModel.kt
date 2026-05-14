package com.example.linter.presentation.ui.createlecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linter.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreateLectureViewModel : ViewModel() {
    private val lectureRepository = AppModule.lectureRepository
    private val processLectureTextUseCase = AppModule.processLectureTextUseCase

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun createLecture(title: String, text: String, language: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                // Получаем созданную лекцию
                val lecture = lectureRepository.createLecture(title, text, language)
                // Передаем ее ID в UseCase для привязки слов
                processLectureTextUseCase(text)
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }
}