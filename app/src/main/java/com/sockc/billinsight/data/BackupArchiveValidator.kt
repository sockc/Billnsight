package com.sockc.billinsight.data

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import java.io.File

/**
 * Bounds and validates .bia extraction before it touches the live SQLite database.
 * Separate JVM-testable entry point for wrong-password/corruption/oversized archives.
 */
object BackupArchiveValidator {
    const val MAX_SQLITE_BYTES: Long = 512L * 1024 * 1024
    private const val ENTRY_NAME = "bill_insight.db"

    fun extractEncrypted(archive: File, password: CharArray, destination: File): File {
        require(archive.isFile && archive.length() in 1..(MAX_SQLITE_BYTES + 8L*1024*1024)) {
            "备份文件过大或为空"
        }
        val zip = ZipFile(archive, password)
        val files = zip.fileHeaders
        require(zip.isEncrypted && files.size == 1) { "不是有效的 BillInsight 加密备份" }
        val header: FileHeader = files.single()
        require(!header.isDirectory && header.fileName == ENTRY_NAME) {
            "备份目录结构无效"
        }
        require(header.isEncrypted && header.uncompressedSize in 1L..MAX_SQLITE_BYTES) {
            "备份内容过大、为空或未加密"
        }
        destination.mkdirs()
        val output = File(destination, ENTRY_NAME)
        try {
            zip.getInputStream(header).use { input ->
                output.outputStream().use { stream ->
                    val buffer = ByteArray(64*1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        copied += n
                        require(copied <= MAX_SQLITE_BYTES) { "解压后文件超过安全上限" }
                        stream.write(buffer,0,n)
                    }
                    require(copied > 0L) { "备份内容为空" }
                }
            }
        } catch (error: Exception) {
            output.delete()
            throw IllegalArgumentException("无法解密备份；请检查密码或文件是否损坏：${error.message}", error)
        }
        return output
    }
}
