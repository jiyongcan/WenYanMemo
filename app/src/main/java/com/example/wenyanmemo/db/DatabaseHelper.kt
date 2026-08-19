package com.example.wenyanmemo.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.wenyanmemo.model.MeaningItem
import com.example.wenyanmemo.model.WordItem
import com.example.wenyanmemo.model.WordWithMeanings
import java.text.Collator
import java.util.Locale

/**
 * SQLite 存储：
 *  words(字词) 1 ── n meanings(释义)，支持同一字词分次添加多条释义，释义可删改。
 */
class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

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
    }

    /** 修改释义 */
    fun updateMeaning(id: Long, pos: String, definition: String) {
        writableDatabase.update("meanings", ContentValues().apply {
            put("pos", pos)
            put("definition", definition)
        }, "id = ?", arrayOf(id.toString()))
    }

    /** 删除单条释义 */
    fun deleteMeaning(id: Long) {
        writableDatabase.delete("meanings", "id = ?", arrayOf(id.toString()))
    }

    /** 删除整个字词（级联删除其释义） */
    fun deleteWord(wordId: Long) {
        writableDatabase.delete("words", "id = ?", arrayOf(wordId.toString()))
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
