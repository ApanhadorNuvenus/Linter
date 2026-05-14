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

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun createLecture(title: String, text: String, language: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                // В новой архитектуре мы просто сохраняем лекцию в базу.
                // Токенизация и поиск слов происходят на лету при открытии LectureDetailScreen.
                lectureRepository.createLecture(title, text, language)
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }
}