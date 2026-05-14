package com.example.linter.data.model

import com.example.linter.domain.model.TextTokenizer
import com.example.linter.domain.model.Token
import java.text.BreakIterator

class AndroidBreakIteratorTokenizer : TextTokenizer {
    override fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val boundary = BreakIterator.getWordInstance()
        boundary.setText(text)
        var start = boundary.first()
        var end = boundary.next()
        while (end != BreakIterator.DONE) {
            val word = text.substring(start, end)
            val isWord = word.any { it.isLetter() }
            tokens.add(Token(word, start, end, isWord))
            start = end
            end = boundary.next()
        }
        return tokens
    }
}