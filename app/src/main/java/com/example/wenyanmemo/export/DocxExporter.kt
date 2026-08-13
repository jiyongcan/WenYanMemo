package com.example.wenyanmemo.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.example.wenyanmemo.model.WordWithMeanings
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxExporter {

    private const val MIME_DOCX =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** 导出全部字词为 .docx，Android 10+ 存"下载"，以下直接写公共下载目录 */
    fun export(context: Context, words: List<WordWithMeanings>): Uri? {
        val bytes = buildDocx(words)
        val name = "文言字词表_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".docx"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, bytes, name)
        } else {
            saveToLegacy(context, bytes, name)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(context: Context, bytes: ByteArray, name: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_DOCX)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacy(context: Context, bytes: ByteArray, name: String): Uri? {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        return try {
            file.writeBytes(bytes)
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }

    /** 生成 docx（zip 容器：Content_Types + rels + document.xml） */
    fun buildDocx(words: List<WordWithMeanings>): ByteArray {
        val documentXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")

            // 标题
            append("<w:p><w:pPr><w:jc w:val=\"center\"/><w:spacing w:after=\"240\"/></w:pPr>")
            append("<w:r><w:rPr><w:b/><w:sz w:val=\"36\"/><w:color w:val=\"333333\"/></w:rPr>")
            append("<w:t>文言字词表</w:t></w:r></w:p>")

            if (words.isEmpty()) {
                append("<w:p><w:r><w:t>（暂无数据）</w:t></w:r></w:p>")
            }

            for (w in words) {
                // 字词标题（加粗）
                append("<w:p><w:pPr><w:spacing w:before=\"200\" w:after=\"40\"/></w:pPr>")
                append("<w:r><w:rPr><w:b/><w:sz w:val=\"28\"/></w:rPr>")
                append("<w:t>${escape(w.word.term)}</w:t></w:r></w:p>")

                // 各条释义
                for (m in w.meanings) {
                    append("<w:p><w:pPr><w:ind w:left=\"360\"/></w:pPr>")
                    append("<w:r><w:rPr><w:i/><w:color w:val=\"666666\"/></w:rPr>")
                    append("<w:t>${escape(m.pos)}</w:t></w:r>")
                    append("<w:r><w:t xml:space=\"preserve\">　</w:t></w:r>")
                    append("<w:r>")
                    m.definition.split("\n").forEachIndexed { i, line ->
                        if (i > 0) append("<w:br/>")
                        append("<w:t xml:space=\"preserve\">${escape(line)}</w:t>")
                    }
                    append("</w:r></w:p>")
                }
            }
            append("</w:body></w:document>")
        }

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>

<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>
""".trimIndent()

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>

<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
""".trimIndent()

        return zip(
            mapOf(
                "[Content_Types].xml" to contentTypes.toByteArray(),
                "_rels/.rels" to rels.toByteArray(),
                "word/document.xml" to documentXml.toByteArray()
            )
        )
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, data) in files) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
