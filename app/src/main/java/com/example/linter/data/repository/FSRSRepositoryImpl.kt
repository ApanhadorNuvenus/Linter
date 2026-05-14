//package com.example.linter.data.repository
//
//import com.example.linter.data.local.ObjectBox
//import com.example.linter.data.local.entity.FlashCardEntity
//import com.example.linter.data.local.entity.WordEntity_
//import com.example.linter.domain.model.Familiarity
//import com.example.linter.domain.repository.FSRSRepository
//import com.example.linter.data.fsrs.FSRS
//import com.example.linter.data.local.entity.FlashCardEntity_
//
//class FSRSRepositoryImpl : FSRSRepository {
//
//    private val flashCardBox get() = ObjectBox.flashCardBox
//    private val wordBox get() = ObjectBox.wordBox
//    private val fsrs = FSRS(requestRetention = 0.9, params = defaultParams)
//
//    override suspend fun onFamiliarityChanged(word: String, newFamiliarity: Familiarity) {
//        val wordEntity = wordBox.query(WordEntity_.word.equal(word.lowercase())).build().findFirst()
//            ?: return
//
//        when (newFamiliarity) {
//            Familiarity.LEARNING -> {
//                val existingCard = flashCardBox.query(FlashCardEntity_.wordId.equal(wordEntity.id)).build().findFirst()
//                if (existingCard == null) {
//                    val newCard = FlashCardEntity(wordId = wordEntity.id)
//                    flashCardBox.put(newCard)
//                    // опционально: сразу рассчитать следующие интервалы (для будущего экрана)
//                }
//            }
//            Familiarity.FAMILIAR, Familiarity.IGNORED -> {
//                flashCardBox.query(FlashCardEntity_.wordId.equal(wordEntity.id)).build().findFirst()?.let {
//                    flashCardBox.remove(it)
//                }
//            }
//            Familiarity.UNKNOWN -> { /* ничего не делаем */ }
//        }
//    }
//
//    companion object {
//        // Стандартные параметры FSRS (можно вынести в настройки)
//        private val defaultParams = listOf(
//            0.4072, 1.1829, 3.1262, 15.4722, 7.2102, 0.5316, 1.0651, 0.0234, 1.616, 0.1544,
//            1.0824, 1.9813, 0.0953, 0.2975, 2.2042, 0.2407, 2.9466, 0.0034, 0.5492, 0.7765, 0.4657
//        )
//    }
//}