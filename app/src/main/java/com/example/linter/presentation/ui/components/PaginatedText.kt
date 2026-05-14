package com.example.linter.presentation.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.linter.domain.model.Familiarity
import com.example.linter.domain.model.Token
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
    modifier: Modifier = Modifier,
    pageHeight: Dp = 600.dp,
    onWordClick: (Int) -> Unit,
    onSelectionStart: (Int) -> Unit,
    onSelectionDrag: (Int) -> Unit,
    onSelectionEnd: () -> Unit,
    onClearSelection: () -> Unit
) {
    val density = LocalDensity.current
    val pageHeightPx = with(density) { pageHeight.toPx() }
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp)
    val textMeasurer = rememberTextMeasurer()
    val maxWidth = with(density) { 360.dp.toPx() }

    // 1. Разбивка на страницы
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

    if (pageCharRanges.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { pageCharRanges.size })

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top
    ) { pageIndex ->
        val charRange = pageCharRanges[pageIndex]
        val pageStartOffset = charRange.first
        val pageText = text.substring(charRange)

        val annotated = buildAnnotatedString {
            append(pageText)

            // Сначала слова (базовый слой)
            tokens.filter { it.startIndex >= pageStartOffset && it.endIndex <= charRange.last + 1 && it.isWord }.forEach { token ->
                val meta = wordMetadata[token.value.lowercase()]
                val bgColor = getFamiliarityColor(meta?.familiarity)
                if (bgColor != Color.Transparent) {
                    addStyle(
                        style = SpanStyle(background = bgColor),
                        start = token.startIndex - pageStartOffset,
                        end = token.endIndex - pageStartOffset
                    )
                }
            }

            // Затем фразы (перекрывают слова)
            phraseRanges.forEach { (range, meta) ->
                val intersectStart = max(range.first, pageStartOffset)
                val intersectEnd = min(range.last + 1, charRange.last + 1)
                if (intersectStart < intersectEnd) {
                    val bgColor = getFamiliarityColor(meta.familiarity)
                    if (bgColor != Color.Transparent) {
                        addStyle(
                            style = SpanStyle(background = bgColor),
                            start = intersectStart - pageStartOffset,
                            end = intersectEnd - pageStartOffset
                        )
                    }
                }
            }

            // В конце - активное выделение (самый верхний слой с прозрачностью)
            if (selectionRange != null) {
                val intersectStart = max(selectionRange.first, pageStartOffset)
                val intersectEnd = min(selectionRange.last + 1, charRange.last + 1)
                if (intersectStart < intersectEnd) {
                    addStyle(
                        style = SpanStyle(background = Color.Gray.copy(alpha = 0.4f)),
                        start = intersectStart - pageStartOffset,
                        end = intersectEnd - pageStartOffset
                    )
                }
            }
        }

        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pageHeight)
        ) {
            Text(
                text = annotated,
                style = style,
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier
                    .fillMaxSize()
                    // Блок для обычных кликов
                    .pointerInput(pageStartOffset) {
                        detectTapGestures(
                            onTap = { pos ->
                                val offset = textLayoutResult?.getOffsetForPosition(pos) ?: return@detectTapGestures
                                onWordClick(pageStartOffset + offset)
                            }
                        )
                    }
                    // Блок для выделения фразы (Long Press + Drag)
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

private fun getFamiliarityColor(familiarity: Familiarity?): Color {
    return when (familiarity) {
        Familiarity.UNKNOWN -> Color(0xFFBBDEFB)
        Familiarity.LEARNING -> Color(0xFFFFF9C4)
        Familiarity.FAMILIAR -> Color.Transparent
        else -> Color.Transparent
    }
}