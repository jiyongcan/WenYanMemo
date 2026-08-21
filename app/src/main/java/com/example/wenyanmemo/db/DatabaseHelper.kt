package com.example.wenyanmemo.db

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.wenyanmemo.model.MeaningItem
import com.example.wenyanmemo.model.WordItem
import com.example.wenyanmemo.model.WordWithMeanings
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.concurrent.Executors

/**
 * SQLite 存储：
 *  words(字词) 1 ── n meanings(释义)，支持同一字词分次添加多条释义，释义可删改。
 */
class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val appContext: Context = context.applicationContext
    private val backupExecutor = Executors.newSingleThreadExecutor()

    companion object {
        private const val DB_NAME = "wenyen_dict.db"
        private const val DB_VERSION = 1

        @Volatile
        private var instance: DatabaseHelper? = null

        fun get(context: Context): DatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE words (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "word TEXT NOT NULL UNIQUE, " +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE meanings (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "word_id INTEGER NOT NULL, " +
                "pos TEXT NOT NULL, " +
                "definition TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX idx_meanings_word_id ON meanings(word_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** 添加释义：字词不存在则自动创建，存在则追加新释义 */
    fun addMeaning(word: String, pos: String, definition: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var wordId = queryWordId(db, word)
            if (wordId == -1L) {
                wordId = db.insert("words", null, ContentValues().apply {
                    put("word", word)
                    put("created_at", System.currentTimeMillis())
                })
            }
            db.insert("meanings", null, ContentValues().apply {
                put("word_id", wordId)
                put("pos", pos)
                put("definition", definition)
                put("created_at", System.currentTimeMillis())
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        autoBackup()
    }

    /** 修改释义 */
    fun updateMeaning(id: Long, pos: String, definition: String) {
        writableDatabase.update("meanings", ContentValues().apply {
            put("pos", pos)
            put("definition", definition)
        }, "id = ?", arrayOf(id.toString()))
        autoBackup()
    }

    /** 删除单条释义 */
    fun deleteMeaning(id: Long) {
        writableDatabase.delete("meanings", "id = ?", arrayOf(id.toString()))
        autoBackup()
    }

    /** 删除整个字词（级联删除其释义） */
    fun deleteWord(wordId: Long) {
        writableDatabase.delete("words", "id = ?", arrayOf(wordId.toString()))
        autoBackup()
    }

    // ===== 自动备份：每次数据变更后，把数据库复制到「下载/文言文记忆备份」 =====

    private fun autoBackup() {
        backupExecutor.execute {
            try {
                backupToDownloads()
            } catch (e: Exception) {
                // 备份失败不影响主流程
            }
        }
    }

    private fun backupToDownloads() {
        // Android 9 及以下需要存储权限，未授权时跳过
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val src = File(appContext.getDatabasePath(DB_NAME).path)
        if (!src.exists()) return
        val dirName = "文言文记忆备份"
        val fileName = "wenyen_dict.db"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val relPath = Environment.DIRECTORY_DOWNLOADS + "/" + dirName
            // 先删除旧备份，保持单一备份文件
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(fileName, relPath)
            )
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            try {
                resolver.openOutputStream(uri)?.use { os ->
                    src.inputStream().use { ins -> ins.copyTo(os) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                dirName
            )
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, fileName)
            src.copyTo(dest, overwrite = true)
        }
    }

    /** 按字词或释义搜索；空串返回全部 */
    fun search(query: String): List<WordWithMeanings> {
        val db = readableDatabase
        val like = "%${query.trim()}%"
        val sql = """
            SELECT DISTINCT w.id, w.word, w.created_at
            FROM words w
            LEFT JOIN meanings m ON m.word_id = w.id
            WHERE w.word LIKE ? OR m.definition LIKE ?
            ORDER BY w.word COLLATE NOCASE ASC
        """.trimIndent()
        val result = mutableListOf<WordWithMeanings>()
        db.rawQuery(sql, arrayOf(like, like)).use { c ->
            while (c.moveToNext()) {
                val word = WordItem(c.getLong(0), c.getString(1), c.getLong(2))
                result.add(WordWithMeanings(word, queryMeanings(db, word.id)))
            }
        }
        // 按拼音首字母排序（中英文统一）
        val collator = Collator.getInstance(Locale.CHINA)
        return result.sortedWith(compareBy(collator) { it.word.term })
    }

    private fun queryWordId(db: SQLiteDatabase, word: String): Long {
        db.query("words", arrayOf("id"), "word = ?", arrayOf(word), null, null, null).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return -1L
    }

    private fun queryMeanings(db: SQLiteDatabase, wordId: Long): List<MeaningItem> {
        val list = mutableListOf<MeaningItem>()
        db.query("meanings", null, "word_id = ?", arrayOf(wordId.toString()),
            null, null, "created_at ASC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    MeaningItem(
                        c.getLong(0), c.getLong(1), c.getString(2),
                        c.getString(3), c.getLong(4)
                    )
                )
            }
        }
        return list
    }
}
