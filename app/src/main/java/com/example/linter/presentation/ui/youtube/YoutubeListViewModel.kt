package com.example.linter.presentation.ui.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linter.di.AppModule
import com.example.linter.domain.model.YoutubeVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class YoutubeListState(
    val videos: List<YoutubeVideo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class YoutubeListViewModel : ViewModel() {
    private val repo = AppModule.youtubeRepository

    private val _uiState = MutableStateFlow(YoutubeListState())
    val uiState: StateFlow<YoutubeListState> = _uiState.asStateFlow()

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(videos = repo.getSavedVideos())
        }
    }

    fun addVideo(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = repo.extractAndSaveVideo(url)

            if (result.isSuccess) {
                val updatedVideos = repo.getSavedVideos()
                _uiState.value = _uiState.value.copy(
                    videos = updatedVideos,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Ошибка загрузки"
                )
            }
        }
    }

    fun deleteVideo(id: Long) {
        viewModelScope.launch {
            // Удаляем видео и все связанные с ним закэшированные субтитры
            repo.deleteVideo(id)
            loadVideos()
        }
    }
}