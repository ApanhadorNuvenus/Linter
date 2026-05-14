package com.example.linter.presentation.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linter.domain.model.Token
import com.example.linter.domain.model.UiWordStatus
import com.example.linter.domain.model.WordMeta
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaginatedLectureText(
    text: String,
    tokens: List<Token>,
    wordMetadata: Map<String, WordMeta>,
    phraseRanges: List<Pair<IntRange, WordMeta>>,
    selectionRange: IntRange?,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    pageHeight: Dp = 500.dp,
    onWordClick: (Int) -> Unit,
    onSelectionStart: (Int) -> Unit,
    onSelectionDrag: (Int) -> Unit,
    onSelectionEnd: () -> Unit,
    onClearSelection: () -> Unit,
    onPageCalculated: (List<IntRange>) -> Unit
) {
    val density = LocalDensity.current
    val pageHeightPx = with(density) { pageHeight.toPx() }

    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, lineHeight = 36.sp)
    val textMeasurer = rememberTextMeasurer()
    val maxWidth = with(density) { 360.dp.toPx() }

    val pageCharRanges = remember(text, style, maxWidth, pageHeightPx) {
        val layout = textMeasurer.measure(
            text = text,
            style = style,
            constraints = Constraints(maxWidth = maxWidth.toInt())
        )

        val lineCount = layout.lineCount
        if (lineCount == 0) return@remember listOf<IntRange>()

        val ranges = mutableListOf<IntRange>()
        var currentPageStartLine = 0
        var currentHeight = 0f

        for (i in 0 until lineCount) {
            val lineHeight = layout.getLineBottom(i) - if (i > 0) layout.getLineBottom(i - 1) else 0f
            if (currentHeight + lineHeight > pageHeightPx && i > currentPageStartLine) {
                val startOffset = layout.getLineStart(currentPageStartLine)
                val endOffset = layout.getLineEnd(i - 1)
                ranges.add(startOffset until endOffset)

                currentPageStartLine = i
                currentHeight = lineHeight
            } else {
                currentHeight += lineHeight
            }
        }
        val lastStart = layout.getLineStart(currentPageStartLine)
        ranges.add(lastStart until text.length)
        ranges
    }

    LaunchedEffect(pageCharRanges) {
        onPageCalculated(pageCharRanges)
    }

    if (pageCharRanges.isEmpty()) return

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth().height(pageHeight),
        userScrollEnabled = false,
        verticalAlignment = Alignment.Top
    ) { pageIndex ->
        val charRange = pageCharRanges[pageIndex]
        val pageStartOffset = charRange.first
        val pageText = text.substring(charRange)

        val annotated = buildAnnotatedString {
            append(pageText)

            // Сначала рисуем цвета отдельных слов
            tokens.filter { it.startIndex >= pageStartOffset && it.endIndex <= charRange.last + 1 && it.isWord }.forEach { token ->
                val meta = wordMetadata[token.value.lowercase()]
                val bgColor = getWordColor(meta?.status)
                if (bgColor != Color.Transparent) {
                    addStyle(
                        style = SpanStyle(background = bgColor),
                        start = token.startIndex - pageStartOffset,
                        end = token.endIndex - pageStartOffset
                    )
                }
            }

            // Затем фразы перекрывают слова
            phraseRanges.forEach { (range, meta) ->
                val intersectStart = max(range.first, pageStartOffset)
                val intersectEnd = min(range.last + 1, charRange.last + 1)
                if (intersectStart < intersectEnd) {
                    val bgColor = getWordColor(meta.status)
                    if (bgColor != Color.Transparent) {
                        addStyle(SpanStyle(background = bgColor), intersectStart - pageStartOffset, intersectEnd - pageStartOffset)
                    }
                }
            }

            // И активное выделение (самое верхнее)
            if (selectionRange != null) {
                val intersectStart = max(selectionRange.first, pageStartOffset)
                val intersectEnd = min(selectionRange.last + 1, charRange.last + 1)
                if (intersectStart < intersectEnd) {
                    addStyle(SpanStyle(background = Color.Gray.copy(alpha = 0.4f)), intersectStart - pageStartOffset, intersectEnd - pageStartOffset)
                }
            }
        }

        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = annotated,
                style = style,
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pageStartOffset) {
                        detectTapGestures(
                            onTap = { pos ->
                                val offset = textLayoutResult?.getOffsetForPosition(pos) ?: return@detectTapGestures
                                onWordClick(pageStartOffset + offset)
                            }
                        )
                    }
                    .pointerInput(pageStartOffset) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                val offset = textLayoutResult?.getOffsetForPosition(pos) ?: return@detectDragGesturesAfterLongPress
                                onSelectionStart(pageStartOffset + offset)
                            },
                            onDrag = { change, _ ->
                                val offset = textLayoutResult?.getOffsetForPosition(change.position) ?: return@detectDragGesturesAfterLongPress
                                onSelectionDrag(pageStartOffset + offset)
                            },
                            onDragEnd = { onSelectionEnd() },
                            onDragCancel = { onClearSelection() }
                        )
                    }
            )
        }
    }
}

private fun getWordColor(status: UiWordStatus?): Color {
    return when (status) {
        UiWordStatus.BLUE, null -> Color(0xFFE3F2FD) // Светло-синий (Новое)
        UiWordStatus.YELLOW -> Color(0xFFFFF9C4)     // Желтый (Учим)
        UiWordStatus.TRANSPARENT -> Color.Transparent // Прозрачный (Знаем/Игнорируем)
    }
}