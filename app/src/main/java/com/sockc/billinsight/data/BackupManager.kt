package com.sockc.billinsight.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.util.UUID

/**
 * Offline AES-256 encrypted .bia archive containing a single SQLite snapshot.
 * No password or bill data is sent to a server.
 */
class BackupManager(
    private val context: Context,
    private val helper: BillDatabase,
) {
    fun exportEncrypted(uri: Uri, password: String) {
        validatePassword(password)
        val work = newWorkDir()
        try {
            val snapshot = File(work, DB_NAME)
            synchronized(helper) {
                // Closing the helper checkpoints SQLite WAL before the file is copied.
                helper.writableDatabase
                helper.close()
                try {
                    val source = context.getDatabasePath(DB_NAME)
                    check(source.isFile) { "账本数据库不存在" }
                    source.copyTo(snapshot, overwrite = true)
                } finally {
                    helper.writableDatabase
                }
            }
            validateSnapshot(snapshot)
            val archive = File(work, "BillInsight.bia")
            val params = ZipParameters().apply {
                fileNameInZip = DB_NAME
                compressionMethod = CompressionMethod.DEFLATE
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            ZipFile(archive, password.toCharArray()).addFile(snapshot, params)
            require(archive.isFile && archive.length() > 0) { "创建加密备份失败" }
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                archive.inputStream().use { it.copyTo(output) }
            } ?: error("无法写入备份文件")
        } finally {
            work.deleteRecursively()
        }
    }

    fun restoreEncrypted(uri: Uri, password: String) {
        validatePassword(password)
        val work = newWorkDir()
        try {
            val archive = File(work, "input.bia")
            context.contentResolver.openInputStream(uri)?.use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= BackupArchiveValidator.MAX_SQLITE_BYTES + 8L*1024*1024) {
                            "备份文件超过安全上限"
                        }
                        output.write(buffer, 0, n)
                    }
                    require(total > 0L) { "备份文件为空" }
                }
            } ?: error("无法读取备份文件")

            val extracted = File(work, "extracted")
            val snapshot = BackupArchiveValidator.extractEncrypted(
                archive, password.toCharArray(), extracted
            )
            require(snapshot.isFile && snapshot.length() <= MAX_BACKUP_BYTES) {
                "备份内容无效"
            }
            validateSnapshot(snapshot)

            synchronized(helper) {
                helper.writableDatabase
                helper.close()
                val current = context.getDatabasePath(DB_NAME)
                val rollback = File(current.parentFile, "$DB_NAME.pre_restore")
                val staged = File(current.parentFile, "$DB_NAME.new")
                try {
                    require(!rollback.exists()) { "检测到未清理的上次恢复副本，请先检查原数据" }
                    current.copyTo(rollback)
                    snapshot.copyTo(staged, overwrite = true)
                    require(staged.renameTo(current)) { "无法替换数据库，请检查存储空间" }
                    File(current.absolutePath + "-wal").delete()
                    File(current.absolutePath + "-shm").delete()
                    // This also migrates a valid older database schema when necessary.
                    helper.writableDatabase.rawQuery("PRAGMA quick_check", null).use { result ->
                        check(result.moveToFirst() && result.getString(0) == "ok") {
                            "恢复的数据库未通过完整性检查"
                        }
                    }
                    rollback.delete()
                } catch (error: Exception) {
                    helper.close()
                    if (rollback.isFile) {
                        rollback.copyTo(current, overwrite = true)
                        File(current.absolutePath + "-wal").delete()
                        File(current.absolutePath + "-shm").delete()
                        helper.writableDatabase
                        rollback.delete()
                    }
                    throw error
                } finally {
                    staged.delete()
                    helper.writableDatabase
                }
            }
        } finally {
            work.deleteRecursively()
        }
    }

    private fun validateSnapshot(file: File) {
        require(file.length() in 1..MAX_BACKUP_BYTES) { "数据库大小无效" }
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use { result ->
                check(result.moveToFirst() && result.getString(0) == "ok") {
                    "SQLite 数据库完整性检查失败"
                }
            }
            db.rawQuery("PRAGMA user_version", null).use { result ->
                check(result.moveToFirst() && result.getInt(0) in 1..15) {
                    "不支持此备份的数据库版本"
                }
            }
            db.rawQuery("SELECT 1 FROM transactions LIMIT 1", null).use { it.moveToFirst() }
            db.rawQuery("SELECT 1 FROM merchant_rules LIMIT 1", null).use { it.moveToFirst() }
        }
    }

    private fun validatePassword(password: String) {
        require(password.length >= 8) { "备份密码至少需要 8 位" }
    }

    private fun newWorkDir(): File =
        File(context.cacheDir, "bill-backup-" + UUID.randomUUID().toString()).apply { mkdirs() }

    companion object {
        private const val DB_NAME = "bill_insight.db"
        private const val MAX_BACKUP_BYTES = 512L * 1024L * 1024L
    }
}
