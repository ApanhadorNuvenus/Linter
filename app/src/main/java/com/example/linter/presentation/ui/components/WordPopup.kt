package com.example.linter.presentation.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.linter.domain.model.LearningStatus
import com.example.linter.domain.model.MultiTranslation
import com.example.linter.presentation.ui.lecturedetail.PopupState
import com.example.linter.presentation.ui.review.getTrimmedContext

@Composable
fun TranslationCard(title: String, text: String?, icon: String, accentColor: Color) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (text == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accentColor.copy(alpha = 0.4f),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun MultiTranslationView(translations: MultiTranslation) {
    val dark = isSystemInDarkTheme()
    val prefs = LocalContext.current.getSharedPreferences("linter_settings", Context.MODE_PRIVATE)

    val showMl = prefs.getBoolean("pref_show_ml_kit", true)
    val showOnnx = prefs.getBoolean("pref_show_onnx", true)
    val showCloud = prefs.getBoolean("pref_show_cloud", true)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showMl) {
            TranslationCard("Google ML Kit", translations.mlKit, "☁️", if (dark) Color(0xFF60A5FA) else Color(0xFF1D4ED8))
        }
        if (showOnnx) {
            TranslationCard("OPUS-MT (Офлайн)", translations.onnx, "🤖", if (dark) Color(0xFF34D399) else Color(0xFF047857))
        }
        if (showCloud) {
            TranslationCard("Cloud API", translations.cloud, "⚡", if (dark) Color(0xFFFBBF24) else Color(0xFFB45309))
        }
    }
}

@Composable
fun WordPopup(
    state: PopupState,
    onStartLearning: (word: String, translations: MultiTranslation, context: String) -> Unit,
    onMarkAsKnown: (word: String, cardId: Long?) -> Unit,
    onMarkAsIgnored: (word: String) -> Unit,
    onChangeLearningStatus: (cardId: Long, word: String, status: LearningStatus) -> Unit,
    onPlayTts: (String) -> Unit,
    onDismiss: () -> Unit,
    onSaveCustomTranslation: (cardId: Long, word: String, translation: String) -> Unit = { _, _, _ -> }
) {
    if (state is PopupState.Hidden) return

    val prefs = LocalContext.current.getSharedPreferences("linter_settings", Context.MODE_PRIVATE)

    var isEditingCustom by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }

    val title = when (state) {
        is PopupState.NewWord -> state.wordOrPhrase
        is PopupState.LearningWord -> state.wordOrPhrase
        else -> ""
    }

    // ИСПРАВЛЕНИЕ: Запускаем эффект только при смене слова (title), а не при потоковом обновлении переводов.
    // Это гарантирует ровно ОДНО воспроизведение звука, как только отрисовалось окно для нового слова.
    LaunchedEffect(title) {
        isEditingCustom = false
        customInput = ""

        if (title.isNotBlank() && prefs.getBoolean("pref_auto_tts", true)) {
            onPlayTts(title)
        }
    }

    val trimmedContextResult = remember(state) {
        val sentence = when (state) {
            is PopupState.NewWord -> state.contextSentence
            is PopupState.LearningWord -> state.contextSentence
            else -> ""
        }
        val target = when (state) {
            is PopupState.NewWord -> state.wordOrPhrase
            is PopupState.LearningWord -> state.wordOrPhrase
            else -> ""
        }
        getTrimmedContext(sentence, target, maxWordsAround = 4)
    }

    val shouldShowContext = remember(state) {
        val target = when (state) {
            is PopupState.NewWord -> state.wordOrPhrase
            is PopupState.LearningWord -> state.wordOrPhrase
            else -> ""
        }.trim()

        val sentence = when (state) {
            is PopupState.NewWord -> state.contextSentence
            is PopupState.LearningWord -> state.contextSentence
            else -> ""
        }.trim()

        if (target.isBlank() || sentence.isBlank()) {
            false
        } else {
            val wordCount = target.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
            val isShortPhrase = wordCount < 4
            val isBelowCoverageThreshold = (target.length.toFloat() / sentence.length.toFloat()) < 0.7f

            isShortPhrase && isBelowCoverageThreshold
        }
    }

    val titleWordCount = remember(title) { title.split(Regex("\\s+")).filter { it.isNotEmpty() }.size }
    val titleStyle = if (titleWordCount > 4) {
        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 26.sp)
    } else {
        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onPlayTts(title) }) {
                        Text("🔊", fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (state) {
                    is PopupState.NewWord -> {
                        if (isEditingCustom) {
                            OutlinedTextField(
                                value = customInput,
                                onValueChange = { customInput = it },
                                label = { Text("Свой перевод") },
                                placeholder = { Text("Введите перевод слова...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    IconButton(onClick = { isEditingCustom = false }) {
                                        Icon(Icons.Default.Check, contentDescription = "Done")
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        } else {
                            if (customInput.isNotBlank()) {
                                TranslationCard(
                                    title = "Ваш перевод (Черновик)",
                                    text = customInput,
                                    icon = "✍️",
                                    accentColor = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            } else {
                                OutlinedButton(
                                    onClick = { isEditingCustom = true },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Добавить свой перевод")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        MultiTranslationView(state.translations)
                        Spacer(modifier = Modifier.height(16.dp))

                        if (shouldShowContext) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(36.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(10.dp))

                                val annotatedContext = buildAnnotatedString {
                                    append(trimmedContextResult.text)
                                    trimmedContextResult.targetRange?.let { range ->
                                        addStyle(
                                            SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                textDecoration = TextDecoration.Underline,
                                                color = MaterialTheme.colorScheme.primary
                                            ),
                                            range.first,
                                            range.last + 1
                                        )
                                    }
                                }
                                Text(
                                    text = annotatedContext,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    is PopupState.LearningWord -> {
                        val savedCustom = state.meta.translations?.custom

                        if (!state.meta.hasFlashCard) {
                            Button(
                                onClick = {
                                    onChangeLearningStatus(
                                        state.meta.contextCardId!!,
                                        state.wordOrPhrase,
                                        state.meta.learningStatus ?: LearningStatus.NEW
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Добавить в повторение (РП)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (isEditingCustom) {
                            OutlinedTextField(
                                value = customInput,
                                onValueChange = { customInput = it },
                                label = { Text("Свой перевод") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            onSaveCustomTranslation(state.meta.contextCardId!!, state.wordOrPhrase, customInput)
                                            isEditingCustom = false
                                        }
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Save")
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        } else {
                            if (!savedCustom.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        TranslationCard(
                                            title = "Ваш перевод",
                                            text = savedCustom,
                                            icon = "✍️",
                                            accentColor = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            customInput = savedCustom
                                            isEditingCustom = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        customInput = ""
                                        isEditingCustom = true
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Добавить свой перевод")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        state.meta.translations?.let { MultiTranslationView(it) }
                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Learning Level",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LearningStatus.entries.forEach { status ->
                                val isSelected = state.meta.learningStatus == status
                                val animatedColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    label = "tabBg"
                                )
                                val textColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    label = "tabText"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(animatedColor)
                                        .clickable {
                                            onChangeLearningStatus(state.meta.contextCardId!!, state.wordOrPhrase, status)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = status.name.take(4),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state is PopupState.NewWord) {
                        TextButton(onClick = { onMarkAsIgnored(state.wordOrPhrase) }) {
                            Text("Ignore", color = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onMarkAsKnown(state.wordOrPhrase, null) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("I Know It")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val finalTranslations = if (customInput.isNotBlank()) {
                                    state.translations.copy(custom = customInput.trim())
                                } else {
                                    state.translations
                                }
                                onStartLearning(state.wordOrPhrase, finalTranslations, state.contextSentence)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Learn")
                        }
                    } else if (state is PopupState.LearningWord) {
                        TextButton(onClick = onDismiss) {
                            Text("Close", color = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onMarkAsKnown(state.wordOrPhrase, state.meta.contextCardId) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("✓ Known")
                        }
                    }
                }
            }
        }
    }
}