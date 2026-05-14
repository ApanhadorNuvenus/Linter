package com.example.linter.presentation.ui.createlecture

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CreateLectureScreen(
    onCreated: () -> Unit,
    viewModel: CreateLectureViewModel = viewModel()
) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("en") }
    val isProcessing by viewModel.isProcessing.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        TextField(value = title, onValueChange = { title = it }, label = { Text("Название лекции") })
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Текст лекции") },
            modifier = Modifier.fillMaxWidth().height(200.dp),
            maxLines = 10
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            RadioButton(selected = selectedLanguage == "en", onClick = { selectedLanguage = "en" })
            Text("Английский")
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(selected = selectedLanguage == "fr", onClick = { selectedLanguage = "fr" })
            Text("Французский")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                viewModel.createLecture(title, text, selectedLanguage) { onCreated() }
            },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp))
            else Text("Создать лекцию")
        }
    }
}