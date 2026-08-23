package com.lasse.speedometer.data.io

/** Thrown when a backup file is not the JSON it claims to be. */
class JsonParseException(message: String) : Exception(message)

/**
 * Just enough JSON to read a backup file back.
 *
 * Android ships `org.json`, but it is a stub on the JVM, so a parser written
 * here is one that can be tested — and a backup restore is exactly the code
 * that must not be discovered to be broken at the moment it is needed.
 *
 * Values come back as the obvious Kotlin types: `Map<String, Any?>`,
 * `List<Any?>`, `String`, `Double`, `Boolean` and `null`.
 */
object MiniJson {

    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd) throw JsonParseException("Unexpected content at ${parser.index}")
        return value
    }

    /** Quotes and escapes a string for writing into JSON. */
    fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (character < ' ') {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private class Parser(private val text: String) {
        var index = 0
            private set

        val atEnd: Boolean get() = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun readValue(): Any? {
            if (atEnd) throw JsonParseException("Unexpected end of input")
            return when (val character = text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (character == '-' || character.isDigit()) {
                    readNumber()
                } else {
                    throw JsonParseException("Unexpected '$character' at $index")
                }
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val entries = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                index++
                return entries
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                entries[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return entries
                    }

                    else -> throw JsonParseException("Expected ',' or '}' at $index")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val values = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                return values
            }
            while (true) {
                skipWhitespace()
                values += readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return values
                    }

                    else -> throw JsonParseException("Expected ',' or ']' at $index")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (atEnd) throw JsonParseException("Unterminated string")
                when (val character = text[index++]) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (atEnd) throw JsonParseException("Unterminated escape")
                        when (val escape = text[index++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) {
                                    throw JsonParseException("Truncated \\u escape")
                                }
                                val code = text.substring(index, index + 4).toIntOrNull(16)
                                    ?: throw JsonParseException("Bad \\u escape at $index")
                                builder.append(code.toChar())
                                index += 4
                            }

                            else -> throw JsonParseException("Bad escape '\\$escape'")
                        }
                    }

                    else -> builder.append(character)
                }
            }
        }

        private fun readNumber(): Double {
            val start = index
            if (peek() == '-') index++
            while (!atEnd && (text[index].isDigit() || text[index] in ".eE+-")) index++
            return text.substring(start, index).toDoubleOrNull()
                ?: throw JsonParseException("Bad number at $start")
        }

        private fun <T> readLiteral(literal: String, value: T): T {
            if (!text.startsWith(literal, index)) {
                throw JsonParseException("Expected $literal at $index")
            }
            index += literal.length
            return value
        }

        private fun peek(): Char =
            if (atEnd) throw JsonParseException("Unexpected end of input") else text[index]

        private fun expect(character: Char) {
            if (atEnd || text[index] != character) {
                throw JsonParseException("Expected '$character' at $index")
            }
            index++
        }
    }
}

/** Typed reads that treat a missing or wrong-typed field as absent. */
@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.array(key: String): List<Any?> =
    (this[key] as? List<Any?>).orEmpty()

internal fun Map<String, Any?>.string(key: String): String? = this[key] as? String

internal fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Double)

internal fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Double)?.toLong()

internal fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean
