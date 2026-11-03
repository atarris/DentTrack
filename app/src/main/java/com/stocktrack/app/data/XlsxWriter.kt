package com.stocktrack.app.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal .xlsx writer with no third-party dependencies (an .xlsx file is a zip of XML parts).
 * Supports: several sheets, bold header row on a dark fill, frozen header, auto-filter,
 * column widths, numeric/text cells, highlighted rows and pie / bar / stacked column charts.
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
        val emphasisRows: Set<Int> = emptySet(),
        /** Zero-based indexes into [rows] that get a very light green background. */
        val bandRows: Set<Int> = emptySet(),
        /** Zero-based indexes into [rows] that get a strong line above them. */
        val dividerRows: Set<Int> = emptySet(),
        /** Zero-based indexes into [rows] styled like the header row (a second table on the sheet). */
        val headerRows: Set<Int> = emptySet(),
        /** Zero-based indexes into [rows] shown as a large section title. */
        val titleRows: Set<Int> = emptySet(),
        /** false = a report page: no frozen header, no filter buttons and no grid lines. */
        val dataTable: Boolean = true,
        val charts: List<Chart> = emptyList()
    )

    enum class ChartKind { PIE, BAR_HORIZONTAL, COLUMN_STACKED }

    /** A cell range plus its current values (shown by viewers that do not recalculate). */
    class DataRef(val formula: String, val values: List<Any?>)

    class Series(val name: DataRef, val values: DataRef)

    /** Placed from ([fromCol], [fromRow]) to ([toCol], [toRow]), zero-based sheet cells. */
    class Chart(
        val kind: ChartKind,
        val title: String,
        val categories: DataRef,
        val series: List<Series>,
        val fromCol: Int,
        val fromRow: Int,
        val toCol: Int,
        val toRow: Int
    )

    /** Reference to column [col] of [sheet]; rows are indexes into [Sheet.rows], -1 being the header row. */
    fun range(sheet: String, col: Int, firstRow: Int, lastRow: Int = firstRow): String {
        val name = safeSheetName(sheet).replace("'", "''")
        val c = colName(col)
        return if (firstRow == lastRow) "'$name'!\$$c\$${firstRow + 2}"
        else "'$name'!\$$c\$${firstRow + 2}:\$$c\$${lastRow + 2}"
    }

    /** Reference to columns [firstCol]..[lastCol] of one row, numbered as in [range]. */
    fun rowRange(sheet: String, row: Int, firstCol: Int, lastCol: Int): String {
        val name = safeSheetName(sheet).replace("'", "''")
        return "'$name'!\$${colName(firstCol)}\$${row + 2}:\$${colName(lastCol)}\$${row + 2}"
    }

    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun build(sheets: List<Sheet>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val drawings = sheets.count { it.charts.isNotEmpty() }
            val chartCount = sheets.sumOf { it.charts.size }
            put("[Content_Types].xml", contentTypes(sheets.size, drawings, chartCount))
            put("_rels/.rels", rootRels())
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", styles())
            var drawing = 0
            var chart = 0
            sheets.forEachIndexed { i, s ->
                put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s))
                if (s.charts.isNotEmpty()) {
                    drawing++
                    put(
                        "xl/worksheets/_rels/sheet${i + 1}.xml.rels",
                        XML + "<Relationships xmlns=\"$PKG_REL_NS\"><Relationship Id=\"rId1\" " +
                            "Type=\"$REL_NS/drawing\" Target=\"../drawings/drawing$drawing.xml\"/></Relationships>"
                    )
                    put("xl/drawings/drawing$drawing.xml", drawingXml(s.charts))
                    put("xl/drawings/_rels/drawing$drawing.xml.rels", buildString {
                        append(XML).append("<Relationships xmlns=\"$PKG_REL_NS\">")
                        s.charts.indices.forEach { c ->
                            append("<Relationship Id=\"rId${c + 1}\" Type=\"$REL_NS/chart\" Target=\"../charts/chart${chart + c + 1}.xml\"/>")
                        }
                        append("</Relationships>")
                    })
                    s.charts.forEach { c ->
                        chart++
                        put("xl/charts/chart$chart.xml", chartXml(c))
                    }
                }
            }
        }
        return out.toByteArray()
    }

    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val CHART_NS = "http://schemas.openxmlformats.org/drawingml/2006/chart"
    private const val DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val SHEET_DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"

    /** Chart colours: the app's emerald first, then clearly different hues. */
    private val PALETTE = listOf(
        "087F5B", "F59F00", "1C7ED6", "E8590C", "7048E8", "12B886",
        "D6336C", "5C940D", "0B7285", "A61E4D", "868E96", "364FC7"
    )

    private fun contentTypes(n: Int, drawings: Int, charts: Int): String = buildString {
        append(XML)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        for (i in 1..n) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        for (i in 1..drawings) {
            append("<Override PartName=\"/xl/drawings/drawing$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
        }
        for (i in 1..charts) {
            append("<Override PartName=\"/xl/charts/chart$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\"/>")
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

    // Style indexes: 0 = normal, 1 = header, 2 = highlighted row, 3 = emphasis, 4 = light band,
    // 5 / 6 / 7 = normal / band / emphasis with a line above, 8 = section title
    private fun styles(): String =
        XML + "<styleSheet xmlns=\"$NS\">" +
            "<fonts count=\"4\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"14\"/><color rgb=\"FF075C46\"/><name val=\"Calibri\"/></font>" +
            "</fonts>" +
            "<fills count=\"6\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF087F5B\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFC7CE\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFBDEBD6\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFEEF9F3\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "</fills>" +
            "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>" +
            "<border><left/><right/><top style=\"medium\"><color rgb=\"FF087F5B\"/></top><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"9\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"4\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"5\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"5\" borderId=\"1\" xfId=\"0\" applyFill=\"1\" applyBorder=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"4\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>" +
            "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"

    private fun rowStyle(s: Sheet, r: Int): Int {
        val divider = r in s.dividerRows
        return when {
            r in s.headerRows -> 1
            r in s.titleRows -> 8
            r in s.highlightRows -> 2
            r in s.emphasisRows -> if (divider) 7 else 3
            r in s.bandRows -> if (divider) 6 else 4
            else -> if (divider) 5 else 0
        }
    }

    private fun sheetXml(s: Sheet): String = buildString {
        append(XML)
        append("<worksheet xmlns=\"$NS\" xmlns:r=\"$REL_NS\">")
        if (s.dataTable) {
            append("<sheetViews><sheetView workbookViewId=\"0\">")
            append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
            append("</sheetView></sheetViews>")
        } else {
            append("<sheetViews><sheetView showGridLines=\"0\" workbookViewId=\"0\"/></sheetViews>")
        }
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
            val style = rowStyle(s, r)
            append("<row r=\"$rowNum\">")
            row.forEachIndexed { c, v -> append(cell(c, rowNum, v, style)) }
            append("</row>")
        }
        append("</sheetData>")
        if (s.dataTable && s.headers.isNotEmpty()) {
            val last = colName(s.headers.size - 1)
            append("<autoFilter ref=\"A1:$last${maxOf(s.rows.size + 1, 1)}\"/>")
        }
        if (s.charts.isNotEmpty()) append("<drawing r:id=\"rId1\"/>")
        append("</worksheet>")
    }

    private fun drawingXml(charts: List<Chart>): String = buildString {
        append(XML)
        append("<xdr:wsDr xmlns:xdr=\"$SHEET_DRAWING_NS\" xmlns:a=\"$DRAWING_NS\">")
        charts.forEachIndexed { i, c ->
            append("<xdr:twoCellAnchor>")
            append("<xdr:from><xdr:col>${c.fromCol}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${c.fromRow}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>")
            append("<xdr:to><xdr:col>${c.toCol}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${c.toRow}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>")
            append("<xdr:graphicFrame macro=\"\"><xdr:nvGraphicFramePr>")
            append("<xdr:cNvPr id=\"${i + 2}\" name=\"Chart ${i + 1}\"/><xdr:cNvGraphicFramePr/>")
            append("</xdr:nvGraphicFramePr>")
            append("<xdr:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/></xdr:xfrm>")
            append("<a:graphic><a:graphicData uri=\"$CHART_NS\">")
            append("<c:chart xmlns:c=\"$CHART_NS\" xmlns:r=\"$REL_NS\" r:id=\"rId${i + 1}\"/>")
            append("</a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor>")
        }
        append("</xdr:wsDr>")
    }

    private fun solidFill(hex: String) = "<a:solidFill><a:srgbClr val=\"$hex\"/></a:solidFill>"

    private fun strRef(ref: DataRef): String = buildString {
        append("<c:strRef><c:f>${esc(ref.formula)}</c:f><c:strCache><c:ptCount val=\"${ref.values.size}\"/>")
        ref.values.forEachIndexed { i, v -> append("<c:pt idx=\"$i\"><c:v>${esc(v?.toString().orEmpty())}</c:v></c:pt>") }
        append("</c:strCache></c:strRef>")
    }

    private fun numRef(ref: DataRef): String = buildString {
        append("<c:numRef><c:f>${esc(ref.formula)}</c:f><c:numCache><c:formatCode>General</c:formatCode><c:ptCount val=\"${ref.values.size}\"/>")
        ref.values.forEachIndexed { i, v -> if (v != null) append("<c:pt idx=\"$i\"><c:v>$v</c:v></c:pt>") }
        append("</c:numCache></c:numRef>")
    }

    private fun dataLabels(percent: Boolean, value: Boolean): String = buildString {
        append("<c:dLbls>")
        if (percent) {
            append("<c:txPr><a:bodyPr/><a:p><a:pPr><a:defRPr sz=\"1000\" b=\"1\">${solidFill("FFFFFF")}</a:defRPr></a:pPr><a:endParaRPr lang=\"en-US\"/></a:p></c:txPr>")
        }
        append("<c:showLegendKey val=\"0\"/><c:showVal val=\"${if (value) 1 else 0}\"/><c:showCatName val=\"0\"/>")
        append("<c:showSerName val=\"0\"/><c:showPercent val=\"${if (percent) 1 else 0}\"/><c:showBubbleSize val=\"0\"/>")
        append("</c:dLbls>")
    }

    private fun axes(horizontal: Boolean, showValues: Boolean): String = buildString {
        val line = "<c:spPr><a:ln w=\"9525\">${solidFill("E2EAE5")}</a:ln></c:spPr>"
        // A horizontal bar chart lists its first row at the top, so rank 1 comes first.
        append("<c:catAx><c:axId val=\"111111\"/><c:scaling><c:orientation val=\"${if (horizontal) "maxMin" else "minMax"}\"/></c:scaling>")
        append("<c:delete val=\"0\"/><c:axPos val=\"${if (horizontal) "l" else "b"}\"/><c:numFmt formatCode=\"General\" sourceLinked=\"0\"/>")
        append("<c:majorTickMark val=\"none\"/><c:minorTickMark val=\"none\"/><c:tickLblPos val=\"nextTo\"/>$line")
        append("<c:crossAx val=\"222222\"/><c:crosses val=\"autoZero\"/><c:auto val=\"1\"/><c:lblAlgn val=\"ctr\"/><c:lblOffset val=\"100\"/><c:noMultiLvlLbl val=\"0\"/></c:catAx>")
        append("<c:valAx><c:axId val=\"222222\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling>")
        append("<c:delete val=\"${if (showValues) 0 else 1}\"/><c:axPos val=\"${if (horizontal) "t" else "l"}\"/>")
        if (showValues) append("<c:majorGridlines>$line</c:majorGridlines>")
        append("<c:numFmt formatCode=\"General\" sourceLinked=\"0\"/><c:majorTickMark val=\"none\"/><c:minorTickMark val=\"none\"/><c:tickLblPos val=\"nextTo\"/>")
        append("<c:crossAx val=\"111111\"/><c:crosses val=\"autoZero\"/><c:crossBetween val=\"between\"/></c:valAx>")
    }

    private fun chartXml(c: Chart): String = buildString {
        append(XML)
        append("<c:chartSpace xmlns:c=\"$CHART_NS\" xmlns:a=\"$DRAWING_NS\" xmlns:r=\"$REL_NS\"><c:roundedCorners val=\"0\"/><c:chart>")
        append("<c:title><c:tx><c:rich><a:bodyPr/><a:p><a:pPr><a:defRPr sz=\"1400\" b=\"1\"/></a:pPr>")
        append("<a:r><a:rPr lang=\"en-US\" sz=\"1400\" b=\"1\">${solidFill("075C46")}</a:rPr><a:t>${esc(c.title)}</a:t></a:r></a:p></c:rich></c:tx><c:overlay val=\"0\"/></c:title>")
        append("<c:autoTitleDeleted val=\"0\"/><c:plotArea><c:layout/>")
        when (c.kind) {
            ChartKind.PIE -> {
                val s = c.series.first()
                append("<c:pieChart><c:varyColors val=\"1\"/><c:ser><c:idx val=\"0\"/><c:order val=\"0\"/><c:tx>${strRef(s.name)}</c:tx>")
                c.categories.values.indices.forEach { i ->
                    append("<c:dPt><c:idx val=\"$i\"/><c:bubble3D val=\"0\"/><c:spPr>${solidFill(PALETTE[i % PALETTE.size])}")
                    append("<a:ln w=\"12700\">${solidFill("FFFFFF")}</a:ln></c:spPr></c:dPt>")
                }
                append(dataLabels(percent = true, value = false))
                append("<c:cat>${strRef(c.categories)}</c:cat><c:val>${numRef(s.values)}</c:val></c:ser><c:firstSliceAng val=\"0\"/></c:pieChart>")
            }
            ChartKind.BAR_HORIZONTAL, ChartKind.COLUMN_STACKED -> {
                val horizontal = c.kind == ChartKind.BAR_HORIZONTAL
                append("<c:barChart><c:barDir val=\"${if (horizontal) "bar" else "col"}\"/>")
                append("<c:grouping val=\"${if (horizontal) "clustered" else "stacked"}\"/><c:varyColors val=\"0\"/>")
                c.series.forEachIndexed { i, s ->
                    append("<c:ser><c:idx val=\"$i\"/><c:order val=\"$i\"/><c:tx>${strRef(s.name)}</c:tx>")
                    append("<c:spPr>${solidFill(PALETTE[i % PALETTE.size])}</c:spPr><c:invertIfNegative val=\"0\"/>")
                    if (horizontal) append(dataLabels(percent = false, value = true))
                    append("<c:cat>${strRef(c.categories)}</c:cat><c:val>${numRef(s.values)}</c:val></c:ser>")
                }
                append("<c:gapWidth val=\"${if (horizontal) 45 else 60}\"/>")
                if (!horizontal) append("<c:overlap val=\"100\"/>")
                append("<c:axId val=\"111111\"/><c:axId val=\"222222\"/></c:barChart>")
                append(axes(horizontal, showValues = !horizontal))
            }
        }
        append("</c:plotArea>")
        if (c.kind != ChartKind.BAR_HORIZONTAL) append("<c:legend><c:legendPos val=\"r\"/><c:overlay val=\"0\"/></c:legend>")
        append("<c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart></c:chartSpace>")
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
