package com.sockc.billinsight.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedBillReaderTest {
    @Test fun acceptsSmallStream() {
        val bytes=ByteArray(200) { it.toByte() }
        assertArrayEquals(bytes,BoundedBillReader.read(bytes.inputStream(),500))
    }
    @Test fun rejectsOversizeWithoutReadingEntireStream() {
        val bytes=ByteArray(200)
        var rejected=false
        try { BoundedBillReader.read(bytes.inputStream(),100) }
        catch(_:IllegalArgumentException){ rejected=true }
        assertTrue(rejected)
    }
    @Test fun rejectsEmptyFile() {
        var rejected=false
        try { BoundedBillReader.read(byteArrayOf().inputStream(),10) }
        catch(_:IllegalArgumentException){rejected=true}
        assertTrue(rejected)
    }
}
