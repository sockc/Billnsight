package com.sockc.billinsight.importer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sockc.billinsight.model.Platform
import com.sockc.billinsight.model.Transaction
import com.sockc.billinsight.util.parseAmountToCent
import com.sockc.billinsight.util.parseDateTime
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import java.nio.charset.Charset
import java.security.MessageDigest

class PasswordRequiredException : IllegalStateException("该账单 ZIP 已加密，请输入微信/支付宝提供的解压码")

class BillImporter(
    private val context: Context,
    private val resolver: ContentResolver,
) {

    data class ParsedBill(
        val sourceName: String,
        val platform: Platform,
        val transactions: List<Transaction>,
    )

    fun parse(uri: Uri, merchantRules: Map<String, String>, zipPassword: String? = null): ParsedBill {
        val sourceName = queryName(uri) ?: "账单文件"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取文件")

        val extracted = if (sourceName.endsWith(".zip", true) || isZip(bytes)) {
            extractFirstSupportedFile(bytes, zipPassword)
        } else sourceName to bytes
        val actualName = extracted.first
        val actualBytes = extracted.second

        require(!actualName.endsWith(".xlsx", true) && !actualName.endsWith(".xls", true)) {
            "V0.1 先支持 CSV/ZIP 账单；Excel 账单请先另存为 CSV。"
        }

        val text = decodeText(actualBytes)
        val rows = CsvParser.parse(text)
        require(rows.isNotEmpty()) { "文件中没有可识别的账单内容" }

        val headerIndex = findHeaderRow(rows)
        require(headerIndex >= 0) { "没有找到交易时间/金额/交易对方等账单表头" }
        val headers = rows[headerIndex].map(::normalizeHeader)
        val platform = detectPlatform(headers, text)

        val items = rows.drop(headerIndex + 1).mapNotNull { row ->
            parseRow(row, headers, platform, actualName, merchantRules)
        }
        return ParsedBill(actualName, platform, items)
    }

    private fun parseRow(
        row: List<String>,
        headers: List<String>,
        platform: Platform,
        sourceName: String,
        merchantRules: Map<String, String>,
    ): Transaction? {
        if (row.all { it.isBlank() }) return null
        fun value(vararg aliases: String): String {
            val index = aliases.asSequence()
                .map(::normalizeHeader)
                .map { headers.indexOf(it) }
                .firstOrNull { it >= 0 } ?: return ""
            return row.getOrNull(index)?.trim().orEmpty()
        }

        val time = value("交易时间", "交易创建时间", "付款时间", "创建时间")
        val merchant = value("交易对方", "对方", "商户名称", "交易商户")
        val description = value("商品", "商品名称", "商品说明", "交易商品")
        val direction = value("收/支", "收支", "收支类型")
        val amountRaw = value("金额(元)", "金额（元）", "金额", "交易金额")
        val type = value("交易类型", "类型")
        val payment = value("支付方式", "付款方式", "资金状态")
        val status = value("当前状态", "交易状态", "状态")
        val transactionId = value("交易单号", "交易号", "支付宝交易号")
        val merchantOrderId = value("商户单号", "商家订单号", "商户订单号")
        val amountCent = parseAmountToCent(amountRaw)

        if (time.isBlank() && merchant.isBlank() && description.isBlank() && amountCent == 0L) return null

        val classification = TransactionClassifier.classify(
            direction = direction,
            type = type,
            merchant = merchant,
            description = description,
            status = status,
            merchantRules = merchantRules,
        )
        val occurredAt = parseDateTime(time)
        val fingerprint = fingerprint(
            platform.name,
            transactionId.ifBlank { merchantOrderId },
            time,
            merchant,
            description,
            direction,
            amountCent.toString()
        )
        return Transaction(
            platform = platform,
            occurredAt = occurredAt,
            counterparty = merchant,
            description = description.ifBlank { type },
            directionText = direction,
            amountCent = amountCent,
            flowType = classification.flowType,
            category = classification.category,
            paymentMethod = payment,
            transactionId = transactionId,
            merchantOrderId = merchantOrderId,
            sourceFile = sourceName,
            fingerprint = fingerprint,
        )
    }

    private fun findHeaderRow(rows: List<List<String>>): Int {
        return rows.take(80).indexOfFirst { row ->
            val joined = row.joinToString("|") { normalizeHeader(it) }
            val score = listOf("交易时间", "交易对方", "金额", "收/支", "交易状态", "交易单号")
                .count { joined.contains(normalizeHeader(it)) }
            score >= 3
        }
    }

    private fun detectPlatform(headers: List<String>, text: String): Platform {
        val all = (headers.joinToString("|") + text.take(3000)).lowercase()
        return when {
            all.contains("微信支付") || (all.contains("商户单号") && all.contains("当前状态")) -> Platform.WECHAT
            all.contains("支付宝") || all.contains("商家订单号") || all.contains("交易创建时间") -> Platform.ALIPAY
            else -> Platform.UNKNOWN
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        val candidates = listOf(Charsets.UTF_8, Charset.forName("GB18030"), Charset.forName("GBK"))
        return candidates
            .map { charset -> bytes.toString(charset) }
            .maxByOrNull(::decodeScore)
            ?: bytes.toString(Charsets.UTF_8)
    }

    private fun decodeScore(text: String): Int {
        val keywords = listOf("交易时间", "交易对方", "金额", "收/支", "微信", "支付宝", "交易状态")
        return keywords.sumOf { if (text.contains(it)) 10 else 0 } - text.count { it == '�' } * 5
    }

    private fun extractFirstSupportedFile(zipBytes: ByteArray, password: String?): Pair<String, ByteArray> {
        val temp = java.io.File.createTempFile("bill-", ".zip", context.cacheDir)
        try {
            temp.writeBytes(zipBytes)
            val zip = if (password.isNullOrBlank()) ZipFile(temp) else ZipFile(temp, password.toCharArray())
            if (zip.isEncrypted && password.isNullOrBlank()) throw PasswordRequiredException()
            val header = zip.fileHeaders.firstOrNull { h ->
                !h.isDirectory && listOf(".csv", ".txt", ".xlsx").any { h.fileName.endsWith(it, true) }
            } ?: error("压缩包里没有找到 CSV/TXT/XLSX 账单")
            return header.fileName.substringAfterLast('/') to zip.getInputStream(header).use { it.readBytes() }
        } catch (e: ZipException) {
            if (password.isNullOrBlank()) throw PasswordRequiredException()
            throw IllegalArgumentException("解压失败，请检查解压码是否正确", e)
        } finally {
            temp.delete()
        }
    }

    private fun queryName(uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private fun normalizeHeader(raw: String): String = raw
        .replace("\uFEFF", "")
        .replace(" ", "")
        .replace("\t", "")
        .replace("（", "(")
        .replace("）", ")")
        .trim()
        .lowercase()

    private fun fingerprint(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("\u001F").toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
