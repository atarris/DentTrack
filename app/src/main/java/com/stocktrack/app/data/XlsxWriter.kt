package com.stocktrack.app.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal .xlsx writer with no third-party dependencies (an .xlsx file is a zip of XML parts).
 * Supports: several sheets, bold header row on a dark fill, frozen header, auto-filter,
 * column widths, numeric/text cells and highlighted rows.
 */
object XlsxWriter {

    class Sheet(
        val name: String,
        val headers: List<String>,
        val rows: List<List<Any?>>,
        val widths: List<Int> = emptyList(),
        /** Zero-based indexes into [rows] that get a light-red background. */
        val highlightRows: Set<Int> = emptySet(),
        /** Zero-based indexes into [rows] that get a bold font on a light green background. */
        val emphasisRows: Set<Int> = emptySet()
    )

    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun build(sheets: List<Sheet>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", rootRels())
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", styles())
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
        return out.toByteArray()
    }

    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"

    private fun contentTypes(n: Int): String = buildString {
        append(XML)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        for (i in 1..n) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun rootRels(): String =
        XML + "<Relationships xmlns=\"$PKG_REL_NS\">" +
            "<Relationship Id=\"rId1\" Type=\"$REL_NS/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append(XML)
        append("<workbook xmlns=\"$NS\" xmlns:r=\"$REL_NS\"><sheets>")
        sheets.forEachIndexed { i, s ->
            append("<sheet name=\"${esc(safeSheetName(s.name))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(n: Int): String = buildString {
        append(XML)
        append("<Relationships xmlns=\"$PKG_REL_NS\">")
        for (i in 1..n) {
            append("<Relationship Id=\"rId$i\" Type=\"$REL_NS/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        append("<Relationship Id=\"rId${n + 1}\" Type=\"$REL_NS/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    // Style indexes: 0 = normal, 1 = header, 2 = highlighted row
    private fun styles(): String =
        XML + "<styleSheet xmlns=\"$NS\">" +
            "<fonts count=\"3\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "</fonts>" +
            "<fills count=\"5\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1E8553\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFC7CE\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD3F0DF\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "</fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"4\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"4\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"

    private fun sheetXml(s: Sheet): String = buildString {
        append(XML)
        append("<worksheet xmlns=\"$NS\">")
        append("<sheetViews><sheetView workbookViewId=\"0\">")
        append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        append("</sheetView></sheetViews>")
        if (s.widths.isNotEmpty()) {
            append("<cols>")
            s.widths.forEachIndexed { i, w ->
                append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
            }
            append("</cols>")
        }
        append("<sheetData>")
        // Header row
        append("<row r=\"1\">")
        s.headers.forEachIndexed { c, h -> append(cell(c, 1, h, 1)) }
        append("</row>")
        // Data rows
        s.rows.forEachIndexed { r, row ->
            val rowNum = r + 2
            val style = when {
                r in s.highlightRows -> 2
                r in s.emphasisRows -> 3
                else -> 0
            }
            append("<row r=\"$rowNum\">")
            row.forEachIndexed { c, v -> append(cell(c, rowNum, v, style)) }
            append("</row>")
        }
        append("</sheetData>")
        if (s.headers.isNotEmpty()) {
            val last = colName(s.headers.size - 1)
            append("<autoFilter ref=\"A1:$last${maxOf(s.rows.size + 1, 1)}\"/>")
        }
        append("</worksheet>")
    }

    private fun cell(col: Int, row: Int, value: Any?, style: Int): String {
        val ref = "${colName(col)}$row"
        val s = if (style != 0) " s=\"$style\"" else ""
        return when (value) {
            null -> "<c r=\"$ref\"$s/>"
            is Int, is Long -> "<c r=\"$ref\"$s><v>$value</v></c>"
            is Double -> if (value.isNaN() || value.isInfinite()) "<c r=\"$ref\"$s/>"
            else "<c r=\"$ref\"$s><v>${java.math.BigDecimal(value).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}</v></c>"
            else -> "<c r=\"$ref\"$s t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(value.toString())}</t></is></c>"
        }
    }

    /** 0 -> A, 25 -> Z, 26 -> AA ... */
    internal fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append('A' + (n % 26))
            n = n / 26 - 1
        }
        return sb.reverse().toString()
    }

    private fun safeSheetName(name: String): String =
        name.replace(Regex("[\\\\/?*\\[\\]:]"), " ").take(31).ifBlank { "Sheet" }

    private fun esc(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (ch in text) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch.code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t' -> Unit // invalid in XML
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
