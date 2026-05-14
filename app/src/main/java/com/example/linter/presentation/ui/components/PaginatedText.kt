package com.example.linter.presentation.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linter.domain.model.Familiarity
import com.example.linter.presentation.ui.components.WordMeta
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.Alignment

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaginatedLectureText(
    text: String,
    wordMetadata: Map<String, WordMeta>,
    modifier: Modifier = Modifier,
    pageHeight: Dp = 400.dp,
    onWordClick: (String) -> Unit
) {
    val density = LocalDensity.current
    val pageHeightPx = with(density) { pageHeight.toPx() }
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
    val allTokens = remember(text) {
        // Токенизируем здесь же, чтобы разбить на страницы.
        com.example.linter.data.model.AndroidBreakIteratorTokenizer().tokenize(text)
    }

    // Строим аннотированную строку с расцветкой
    val annotated = buildAnnotatedString {
        allTokens.forEach { token ->
            val start = length
            append(token.value)
            val end = length
            if (token.isWord) {
                val meta = wordMetadata[token.value.lowercase()]
                val familiarity = meta?.familiarity ?: Familiarity.UNKNOWN
                val bgColor = when (familiarity) {
                    Familiarity.UNKNOWN -> Color(0xFFBBDEFB) // голубой
                    Familiarity.LEARNING -> Color(0xFFFFF9C4) // жёлтый
                    else -> Color.Transparent
                }
                if (bgColor != Color.Transparent) {
                    addStyle(SpanStyle(background = bgColor), start, end)
                }
                addStringAnnotation("WORD", token.value, start, end)
            }
        }
    }

    // Измерение текста и разбивка на страницы
    val textMeasurer = rememberTextMeasurer()
    val maxWidth = with(LocalDensity.current) { 360.dp.toPx() } // можно взять из контейнера
    val layout = remember(annotated, style, maxWidth) {
        textMeasurer.measure(
            text = annotated,
            style = style,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidth.toInt()),
            maxLines = Int.MAX_VALUE
        )
    }

    val pages = remember(layout, pageHeightPx) {
        val lineCount = layout.lineCount
        if (lineCount == 0) return@remember listOf<AnnotatedString>()
        val pageLineRanges = mutableListOf<IntRange>()
        var currentPageStartLine = 0
        var currentHeight = 0f
        for (i in 0 until lineCount) {
            val lineHeight = layout.getLineBottom(i) - if (i > 0) layout.getLineBottom(i - 1) else 0f
            if (currentHeight + lineHeight > pageHeightPx && i > currentPageStartLine) {
                pageLineRanges.add(currentPageStartLine until i)
                currentPageStartLine = i
                currentHeight = lineHeight
            } else {
                currentHeight += lineHeight
            }
        }
        pageLineRanges.add(currentPageStartLine until lineCount)

        pageLineRanges.map { range ->
            val startOffset = layout.getLineStart(range.first)
            val endOffset = if (range.last + 1 < lineCount) layout.getLineEnd(range.last) else annotated.length
            annotated.subSequence(startOffset, endOffset)
        }
    }

    if (pages.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { pages.size })

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pageHeight)
                .clickable(enabled = false) { } // обрабатывается отдельно
        ) {
            ClickableText(
                text = pages[page],
                style = style,
                modifier = Modifier.fillMaxSize(),
                onClick = { offset ->
                    pages[page].getStringAnnotations("WORD", offset, offset)
                        .firstOrNull()?.let { annotation ->
                            onWordClick(annotation.item)
                        }
                }
            )
        }
    }
}