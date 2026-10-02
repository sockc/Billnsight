package com.sockc.billinsight.importer

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Prevent oversized local bills or zip entries from exhausting Android process memory. */
object BoundedBillReader {
    const val MAX_SOURCE_BYTES:Long = 40L*1024*1024
    const val MAX_EXTRACTED_BYTES:Long = 64L*1024*1024

    fun read(input:InputStream,maxBytes:Long=MAX_SOURCE_BYTES):ByteArray {
        require(maxBytes in 1..MAX_EXTRACTED_BYTES)
        val output=ByteArrayOutputStream()
        val buffer=ByteArray(64*1024)
        var read=0L
        while(true) {
            val n=input.read(buffer)
            if(n<0) break
            if(n==0) continue
            read+=n
            require(read<=maxBytes) {
                "账单文件超过 "+(maxBytes/(1024*1024))+" MB 安全限制；请按月份分别导出"
            }
            output.write(buffer,0,n)
        }
        require(read>0) { "账单文件为空" }
        return output.toByteArray()
    }
}
