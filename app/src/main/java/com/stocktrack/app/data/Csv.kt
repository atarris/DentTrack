package com.stocktrack.app.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Small CSV reader: quotes, doubled quotes, line breaks inside quotes, BOM, comma / semicolon / tab. */
object Csv {

    /** Decodes UTF-8 (with or without BOM); a file that is not valid UTF-8 is read as Windows-1257 (Baltic). */
    fun decode(bytes: ByteArray): String {
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            val fallback = runCatching { Charset.forName("windows-1257") }.getOrDefault(Charsets.ISO_8859_1)
            String(bytes, fallback)
        }
        return text.removePrefix("﻿")
    }

    /** Picks the separator that is used most often on the first non-empty line (outside quotes). */
    fun detectDelimiter(text: String): Char {
        var inQuotes = false
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        for (c in text) {
            if (c == '"') inQuotes = !inQuotes
            else if (!inQuotes && (c == '\n' || c == '\r')) {
                if (counts.values.any { it > 0 }) break
            } else if (!inQuotes && counts.containsKey(c)) counts[c] = counts.getValue(c) + 1
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        fun endCell() { row.add(cell.toString()); cell.setLength(0) }
        fun endRow() {
            endCell()
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = ArrayList()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else inQuotes = false
                } else cell.append(c)
                c == '"' -> inQuotes = true
                c == delimiter -> endCell()
                c == '\r' -> { endRow(); if (i + 1 < text.length && text[i + 1] == '\n') i++ }
                c == '\n' -> endRow()
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
