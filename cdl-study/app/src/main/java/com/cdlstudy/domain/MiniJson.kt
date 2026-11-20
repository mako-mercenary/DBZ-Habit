package com.cdlstudy.domain

/**
 * A small JSON reader, enough for the dialogue files. It exists so the same
 * parsing code runs in the app and in plain unit tests, without an extra library.
 *
 * Objects become Map<String, Any?>, arrays List<Any?>, strings String,
 * whole numbers Long and other numbers Double, plus Boolean and null.
 */
object MiniJson {

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) reader.fail("Unexpected text after the end of the JSON")
        return value
    }

    private class Reader(private val text: String) {
        private var pos = 0

        fun atEnd() = pos >= text.length

        fun fail(message: String): Nothing {
            val line = text.substring(0, pos.coerceAtMost(text.length)).count { it == '\n' } + 1
            throw IllegalArgumentException("$message (line $line)")
        }

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (atEnd()) fail("Unexpected end of JSON")
            return when (val c = text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readWord("true", true)
                'f' -> readWord("false", false)
                'n' -> readWord("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else fail("Unexpected character '$c'")
            }
        }

        private fun readObject(): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail("Expected a name in quotes")
                val name = readString()
                skipWhitespace()
                if (peek() != ':') fail("Expected ':' after \"$name\"")
                pos++
                result[name] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return result
                    }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        private fun readArray(): List<Any?> {
            val result = ArrayList<Any?>()
            pos++ // [
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return result
            }
            while (true) {
                result += readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return result
                    }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            val result = StringBuilder()
            pos++ // opening quote
            while (true) {
                if (atEnd()) fail("Text is missing its closing quote")
                when (val c = text[pos++]) {
                    '"' -> return result.toString()
                    '\\' -> {
                        if (atEnd()) fail("Text ends in the middle of an escape")
                        when (val escaped = text[pos++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'n' -> result.append('\n')
                            't' -> result.append('\t')
                            'r' -> result.append('\r')
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'u' -> {
                                if (pos + 4 > text.length) fail("Incomplete \\u escape")
                                val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail("Bad \\u escape")
                                result.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("Unknown escape '\\$escaped'")
                        }
                    }
                    '\n' -> fail("Text is missing its closing quote")
                    else -> result.append(c)
                }
            }
        }

        private fun readNumber(): Number {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] in "+-.eE")) pos++
            val literal = text.substring(start, pos)
            // Whole numbers stay exact (timestamps in milliseconds need all their digits).
            return literal.toLongOrNull() ?: literal.toDoubleOrNull() ?: fail("Bad number")
        }

        private fun readWord(word: String, value: Any?): Any? {
            if (!text.startsWith(word, pos)) fail("Unexpected text")
            pos += word.length
            return value
        }

        private fun peek(): Char = if (atEnd()) fail("Unexpected end of JSON") else text[pos]
    }
}
