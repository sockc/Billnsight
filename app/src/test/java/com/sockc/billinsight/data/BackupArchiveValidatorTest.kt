package com.sockc.billinsight.data

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class BackupArchiveValidatorTest {
    @Test fun encryptedBackupRestoresExactOriginalAndWrongPasswordDoesNotCreateSnapshot() {
        val directory = Files.createTempDirectory("bill-backup-unit").toFile()
        try {
            val original = directory.resolve("bill_insight.db")
            val bytes = ByteArray(2048) { (it % 193).toByte() }
            original.writeBytes(bytes)
            val archive = directory.resolve("sample.bia")
            val parameters = ZipParameters().apply {
                fileNameInZip="bill_insight.db"
                isEncryptFiles=true
                encryptionMethod=EncryptionMethod.AES
                aesKeyStrength=AesKeyStrength.KEY_STRENGTH_256
            }
            ZipFile(archive, "correct123".toCharArray()).addFile(original,parameters)
            val restored = BackupArchiveValidator.extractEncrypted(
                archive, "correct123".toCharArray(), directory.resolve("restored")
            )
            assertArrayEquals(bytes,restored.readBytes())
            var rejected=false
            val wrongDestination=directory.resolve("wrong")
            try {
                BackupArchiveValidator.extractEncrypted(
                    archive,"wrong123".toCharArray(),wrongDestination
                )
            } catch (_: IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
            assertFalse(wrongDestination.resolve("bill_insight.db").exists())
        } finally { directory.deleteRecursively() }
    }

    @Test fun unencryptedArchiveIsRejected() {
        val directory=Files.createTempDirectory("bill-backup-plain").toFile()
        try {
            val original=directory.resolve("bill_insight.db")
            original.writeText("not encrypted")
            val zip=directory.resolve("plain.bia")
            ZipFile(zip).addFile(original)
            var rejected=false
            try {
                BackupArchiveValidator.extractEncrypted(zip,"correct123".toCharArray(),directory.resolve("out"))
            } catch (_: IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
        } finally { directory.deleteRecursively() }
    }

    @Test fun invalidArchiveIsRejected() {
        val directory=Files.createTempDirectory("bill-backup-broken").toFile()
        try {
            val broken=directory.resolve("broken.bia")
            broken.writeText("invalid archive")
            var rejected=false
            try {
                BackupArchiveValidator.extractEncrypted(broken,"correct123".toCharArray(),directory.resolve("out"))
            } catch (_: Exception) { rejected=true }
            assertTrue(rejected)
        } finally { directory.deleteRecursively() }
    }
}
