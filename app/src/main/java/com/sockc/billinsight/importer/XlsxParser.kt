package com.sockc.billinsight.importer

import com.sockc.billinsight.util.excelSerialToDateTimeText
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

object XlsxParser {

    data class Sheet(
        val name: String,
        val rows: List<List<String>>,
    )

    fun looksLikeXlsx(bytes: ByteArray): Boolean {
        if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return false
        var hasContentTypes = false
        var hasWorkbook = false
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    "[Content_Types].xml" -> hasContentTypes = true
                    "xl/workbook.xml" -> hasWorkbook = true
                }
                if (hasContentTypes && hasWorkbook) return true
            }
        }
        return false
    }

    fun parse(bytes: ByteArray): List<Sheet> {
        require(looksLikeXlsx(bytes)) { "文件不是有效的 XLSX 工作簿" }

        val entries = readRelevantEntries(bytes)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val date1904 = entries["xl/workbook.xml"]?.let(::parseDate1904) ?: false
        val dateStyles = entries["xl/styles.xml"]?.let(::parseDateStyles).orEmpty()

        val worksheets = entries
            .filterKeys { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .toList()
            .sortedWith(compareBy({ sheetNumber(it.first) }, { it.first }))

        require(worksheets.isNotEmpty()) { "XLSX 中没有找到工作表" }

        return worksheets.map { (path, xml) ->
            Sheet(
                name = path.substringAfterLast('/').substringBeforeLast('.'),
                rows = parseWorksheet(xml, sharedStrings, dateStyles, date1904),
            )
        }
    }

    private fun readRelevantEntries(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                val keep = name == "[Content_Types].xml" ||
                    name == "xl/workbook.xml" ||
                    name == "xl/styles.xml" ||
                    name == "xl/sharedStrings.xml" ||
                    (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                if (keep) result[name] = zip.readBytes()
            }
        }
        return result
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        var inSi = false
        var inText = false
        var current = StringBuilder()

        parseXml(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (element(localName, qName)) {
                    "si" -> {
                        inSi = true
                        current = StringBuilder()
                    }
                    "t" -> if (inSi) inText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inSi && inText) current.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (element(localName, qName)) {
                    "t" -> inText = false
                    "si" -> {
                        strings += current.toString()
                        inSi = false
                    }
                }
            }
        })
        return strings
    }

    private fun parseDate1904(xml: ByteArray): Boolean {
        var enabled = false
        parseXml(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                if (element(localName, qName) == "workbookPr") {
                    val raw = attributes?.getValue("date1904").orEmpty()
                    enabled = raw == "1" || raw.equals("true", true)
                }
            }
        })
        return enabled
    }

    private fun parseDateStyles(xml: ByteArray): Set<Int> {
        val customFormats = mutableMapOf<Int, String>()
        val styleIsDate = mutableSetOf<Int>()
        var inCellXfs = false
        var styleIndex = 0

        parseXml(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (element(localName, qName)) {
                    "numFmt" -> {
                        val id = attributes?.getValue("numFmtId")?.toIntOrNull()
                        val code = attributes?.getValue("formatCode")
                        if (id != null && code != null) customFormats[id] = code
                    }
                    "cellXfs" -> {
                        inCellXfs = true
                        styleIndex = 0
                    }
                    "xf" -> if (inCellXfs) {
                        val numFmtId = attributes?.getValue("numFmtId")?.toIntOrNull() ?: 0
                        if (isDateFormat(numFmtId, customFormats[numFmtId])) {
                            styleIsDate += styleIndex
                        }
                        styleIndex++
                    }
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (element(localName, qName) == "cellXfs") inCellXfs = false
            }
        })

        return styleIsDate
    }

    private fun isDateFormat(numFmtId: Int, customCode: String?): Boolean {
        if (numFmtId in 14..22 || numFmtId in 27..36 || numFmtId in 45..47 || numFmtId in 50..58) {
            return true
        }
        if (customCode.isNullOrBlank()) return false

        val normalized = customCode
            .lowercase()
            .replace(Regex("""\[[^]]*]"""), "")
            .replace("\\", "")

        val hasDate = normalized.contains('y') || normalized.contains('d')
        val hasTimeOrMonth = normalized.contains('m') || normalized.contains('h') || normalized.contains('s')
        return hasDate && hasTimeOrMonth
    }

    private fun parseWorksheet(
        xml: ByteArray,
        sharedStrings: List<String>,
        dateStyles: Set<Int>,
        date1904: Boolean,
    ): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableMapOf<Int, String>()
        var currentCellColumn = -1
        var currentCellType = ""
        var currentCellStyle = -1
        var currentValue = StringBuilder()
        var currentInline = StringBuilder()
        var inValue = false
        var inInlineText = false
        var inCell = false
        var nextColumn = 0

        parseXml(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (element(localName, qName)) {
                    "row" -> {
                        currentRow = mutableMapOf()
                        nextColumn = 0
                    }
                    "c" -> {
                        inCell = true
                        currentCellType = attributes?.getValue("t").orEmpty()
                        currentCellStyle = attributes?.getValue("s")?.toIntOrNull() ?: -1
                        currentCellColumn = attributes?.getValue("r")
                            ?.let(::columnIndex)
                            ?.takeIf { it >= 0 }
                            ?: nextColumn
                        currentValue = StringBuilder()
                        currentInline = StringBuilder()
                    }
                    "v" -> if (inCell) inValue = true
                    "t" -> if (inCell && currentCellType == "inlineStr") inInlineText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue) currentValue.append(ch, start, length)
                if (inInlineText) currentInline.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (element(localName, qName)) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val raw = if (currentCellType == "inlineStr") currentInline.toString() else currentValue.toString()
                        val value = decodeCell(
                            raw = raw,
                            type = currentCellType,
                            style = currentCellStyle,
                            sharedStrings = sharedStrings,
                            dateStyles = dateStyles,
                            date1904 = date1904,
                        )
                        currentRow[currentCellColumn] = value
                        nextColumn = currentCellColumn + 1
                        inCell = false
                    }
                    "row" -> {
                        if (currentRow.isNotEmpty()) {
                            val max = currentRow.keys.maxOrNull() ?: -1
                            val row = MutableList(max + 1) { "" }
                            currentRow.forEach { (column, value) ->
                                if (column in row.indices) row[column] = value
                            }
                            if (row.any { it.isNotBlank() }) rows += row
                        }
                    }
                }
            }
        })

        return rows
    }

    private fun decodeCell(
        raw: String,
        type: String,
        style: Int,
        sharedStrings: List<String>,
        dateStyles: Set<Int>,
        date1904: Boolean,
    ): String {
        val trimmed = raw.trim()
        return when {
            type == "s" -> trimmed.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
            type == "inlineStr" || type == "str" -> raw
            type == "b" -> if (trimmed == "1") "TRUE" else "FALSE"
            style in dateStyles -> trimmed.toDoubleOrNull()?.let { excelSerialToDateTimeText(it, date1904) } ?: trimmed
            else -> trimmed
        }
    }

    private fun columnIndex(cellRef: String): Int {
        var result = 0
        var found = false
        for (ch in cellRef) {
            if (!ch.isLetter()) break
            found = true
            result = result * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return if (found) result - 1 else -1
    }

    private fun sheetNumber(path: String): Int =
        Regex("""sheet(\d+)\.xml$""").find(path)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private fun parseXml(xml: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        factory.newSAXParser().parse(ByteArrayInputStream(xml), handler)
    }

    private fun element(localName: String?, qName: String?): String =
        localName?.takeIf { it.isNotBlank() } ?: qName.orEmpty().substringAfter(':')
}
