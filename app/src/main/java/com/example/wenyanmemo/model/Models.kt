package com.example.wenyanmemo.model

/** 文言字词 */
data class WordItem(val id: Long, val term: String, val createdAt: Long)

/** 一条释义（一个词可有多条） */
data class MeaningItem(
    val id: Long,
    val wordId: Long,
    val pos: String,          // 词性 n. / v. / adj. ...
    val definition: String,   // 释义
    val createdAt: Long
)

/** 字词 + 其全部释义（列表展示用） */
data class WordWithMeanings(val word: WordItem, val meanings: List<MeaningItem>)
