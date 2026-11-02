package com.stocktrack.app.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Minimal .xlsx reader: returns every sheet as rows of text cells. Understands files written by
 * [XlsxWriter] (inline strings) as well as files re-saved by Excel / LibreOffice (shared strings).
 */
object XlsxReader {

    fun read(bytes: ByteArray): Map<String, List<List<String>>> {
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) parts[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }

        val workbook = parts["xl/workbook.xml"] ?: error("workbook.xml missing")
        val rels = parts["xl/_rels/workbook.xml.rels"] ?: error("workbook relationships missing")
        val shared = parts["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val targets = parseRelationships(rels)

        val result = LinkedHashMap<String, List<List<String>>>()
        for ((name, relId) in parseSheetList(workbook)) {
            val target = targets[relId] ?: continue
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            val data = parts[path] ?: continue
            result[name] = parseSheet(data, shared)
        }
        return result
    }

    private fun newParser(data: ByteArray): XmlPullParser =
        Xml.newPullParser().also { it.setInput(ByteArrayInputStream(data), null) }

    private fun parseSheetList(data: ByteArray): List<Pair<String, String>> {
        val p = newParser(data)
        val out = ArrayList<Pair<String, String>>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                val name = p.getAttributeValue(null, "name") ?: continue
                var relId: String? = null
                for (i in 0 until p.attributeCount) {
                    // attribute is written as r:id (the prefix is not resolved by this parser)
                    if (p.getAttributeName(i).endsWith(":id")) relId = p.getAttributeValue(i)
                }
                if (relId != null) out.add(name to relId)
            }
        }
        return out
    }

    private fun parseRelationships(data: ByteArray): Map<String, String> {
        val p = newParser(data)
        val out = HashMap<String, String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                val id = p.getAttributeValue(null, "Id")
                val target = p.getAttributeValue(null, "Target")
                if (id != null && target != null) out[id] = target
            }
        }
        return out
    }

    private fun parseSharedStrings(data: ByteArray): List<String> {
        val p = newParser(data)
        val out = ArrayList<String>()
        var current: StringBuilder? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> current = StringBuilder()
                    "t" -> current?.append(p.nextText())
                }
                XmlPullParser.END_TAG -> if (p.name == "si") {
                    out.add(current?.toString() ?: "")
                    current = null
                }
            }
        }
        return out
    }

    private fun parseSheet(data: ByteArray, shared: List<String>): List<List<String>> {
        val p = newParser(data)
        val rows = ArrayList<MutableList<String>>()
        var row: MutableList<String>? = null
        var col = 0
        var type: String? = null
        var value = StringBuilder()
        var inline = StringBuilder()
        var inInline = false

        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> {
                        row = ArrayList()
                        rows.add(row)
                    }
                    "c" -> {
                        val ref = p.getAttributeValue(null, "r")
                        col = if (ref != null) columnIndex(ref) else (row?.size ?: 0)
                        type = p.getAttributeValue(null, "t")
                        value = StringBuilder()
                        inline = StringBuilder()
                    }
                    "v" -> value.append(p.nextText())
                    "is" -> inInline = true
                    "t" -> if (inInline) inline.append(p.nextText())
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "is" -> inInline = false
                    "c" -> {
                        val text = when (type) {
                            "s" -> shared.getOrElse(value.toString().trim().toIntOrNull() ?: -1) { "" }
                            "inlineStr" -> inline.toString()
                            else -> value.toString()
                        }
                        val r = row
                        if (r != null) {
                            while (r.size < col) r.add("")
                            if (r.size == col) r.add(text) else r[col] = text
                        }
                    }
                }
            }
        }
        return rows
    }

    /** "A1" -> 0, "B7" -> 1, "AA3" -> 26 */
    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return (n - 1).coerceAtLeast(0)
    }
}
