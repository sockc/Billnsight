package com.sockc.billinsight.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {
    @Test
    fun parsesQuotedCommaAndNewline() {
        val rows = CsvParser.parse("交易对方,商品,金额(元)\n\"某商户,分店\",\"咖啡\n大杯\",18.50")
        assertEquals(2, rows.size)
        assertEquals("某商户,分店", rows[1][0])
        assertEquals("咖啡\n大杯", rows[1][1])
        assertEquals("18.50", rows[1][2])
    }
}
