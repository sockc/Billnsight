package com.sockc.billinsight.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxParserTest {

    @Test
    fun parsesWechatLikeXlsxWithSerialDate() {
        val shared = listOf(
            "交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)",
            "支付方式", "当前状态", "交易单号", "商户单号",
            "商户消费", "测试商户", "午餐", "支出", "零钱", "支付成功", "wx001"
        )

        val xlsx = zipOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><workbookPr date1904="0"/></workbook>""",
            "xl/styles.xml" to """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cellXfs count="2"><xf numFmtId="0"/><xf numFmtId="22"/></cellXfs></styleSheet>""",
            "xl/sharedStrings.xml" to buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                shared.forEach { append("<si><t>").append(it).append("</t></si>") }
                append("</sst>")
            },
            "xl/worksheets/sheet1.xml" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c>
                      <c r="E1" t="s"><v>4</v></c><c r="F1" t="s"><v>5</v></c>
                      <c r="G1" t="s"><v>6</v></c><c r="H1" t="s"><v>7</v></c>
                      <c r="I1" t="s"><v>8</v></c><c r="J1" t="s"><v>9</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" s="1"><v>46296.770833333336</v></c>
                      <c r="B2" t="s"><v>10</v></c>
                      <c r="C2" t="s"><v>11</v></c>
                      <c r="D2" t="s"><v>12</v></c>
                      <c r="E2" t="s"><v>13</v></c>
                      <c r="F2"><v>18.50</v></c>
                      <c r="G2" t="s"><v>14</v></c>
                      <c r="H2" t="s"><v>15</v></c>
                      <c r="I2" t="s"><v>16</v></c>
                      <c r="J2" t="inlineStr"><is><t>merchant001</t></is></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
        )

        assertTrue(XlsxParser.looksLikeXlsx(xlsx))
        val rows = XlsxParser.parse(xlsx).single().rows

        assertEquals("交易时间", rows[0][0])
        assertEquals("2026-10-01 18:30:00", rows[1][0])
        assertEquals("测试商户", rows[1][2])
        assertEquals("18.50", rows[1][5])
        assertEquals("merchant001", rows[1][9])
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
