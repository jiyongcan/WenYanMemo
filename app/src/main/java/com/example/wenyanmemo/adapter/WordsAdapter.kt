package com.example.wenyanmemo.adapter

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.wenyanmemo.R
import com.example.wenyanmemo.db.DatabaseHelper
import com.example.wenyanmemo.model.MeaningItem
import com.example.wenyanmemo.model.WordWithMeanings

class WordsAdapter(
    private val db: DatabaseHelper,
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<WordsAdapter.VH>() {

    private val items = mutableListOf<WordWithMeanings>()

    fun submit(list: List<WordWithMeanings>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvWord: TextView = view.findViewById(R.id.tvWord)
        val meaningsContainer: LinearLayout = view.findViewById(R.id.meaningsContainer)
        val tvAddMeaning: TextView = view.findViewById(R.id.tvAddMeaning)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_word, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        holder.tvWord.text = item.word.term
        holder.tvWord.setOnLongClickListener {
            confirmDeleteWord(ctx, item)
            true
        }
        holder.tvAddMeaning.setOnClickListener { showMeaningDialog(ctx, item, null) }

        holder.meaningsContainer.removeAllViews()
        for (m in item.meanings) {
            holder.meaningsContainer.addView(buildMeaningRow(ctx, item, m))
        }
    }

    /** 构造一行释义：内容 + 编辑 + 删除 */
    private fun buildMeaningRow(ctx: Context, wordItem: WordWithMeanings, m: MeaningItem): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6.dp(ctx), 0, 6.dp(ctx))
        }
        row.addView(
            TextView(ctx).apply {
                text = "${m.pos}　${m.definition}"
                textSize = 15f
                setTextColor(0xFF333333.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        row.addView(
            TextView(ctx).apply {
                text = "编辑"
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.primary))
                setPadding(10.dp(ctx), 4.dp(ctx), 10.dp(ctx), 4.dp(ctx))
                setOnClickListener { showMeaningDialog(ctx, wordItem, m) }
            }
        )
        row.addView(
            TextView(ctx).apply {
                text = "删除"
                textSize = 13f
                setTextColor(0xFFE53935.toInt())
                setPadding(10.dp(ctx), 4.dp(ctx), 10.dp(ctx), 4.dp(ctx))
                setOnClickListener { confirmDeleteMeaning(ctx, wordItem, m) }
            }
        )
        return row
    }

    /** 添加 / 编辑释义对话框（同一布局复用） */
    private fun showMeaningDialog(ctx: Context, wordItem: WordWithMeanings, meaning: MeaningItem?) {
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_meaning, null)
        val spPos = dialogView.findViewById<Spinner>(R.id.spPos)
        val etDef = dialogView.findViewById<EditText>(R.id.etDef)
        val posOptions = ctx.resources.getStringArray(R.array.pos_options)

        spPos.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, posOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val isEdit = meaning != null
        if (meaning != null) {
            val idx = posOptions.indexOf(meaning.pos)
            spPos.setSelection(if (idx >= 0) idx else 0)
            etDef.setText(meaning.definition)
        }

        AlertDialog.Builder(ctx)
            .setTitle(if (isEdit) "编辑释义" else "为「${wordItem.word.term}」添加释义")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val pos = spPos.selectedItem.toString()
                val def = etDef.text.toString().trim()
                if (def.isEmpty()) return@setPositiveButton
                if (isEdit) {
                    db.updateMeaning(meaning!!.id, pos, def)
                } else {
                    db.addMeaning(wordItem.word.term, pos, def)
                }
                onDataChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteMeaning(ctx: Context, wordItem: WordWithMeanings, m: MeaningItem) {
        AlertDialog.Builder(ctx)
            .setTitle("删除释义")
            .setMessage("确定删除「${wordItem.word.term}」的释义：${m.pos} ${m.definition}？")
            .setPositiveButton("删除") { _, _ ->
                db.deleteMeaning(m.id)
                onDataChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteWord(ctx: Context, item: WordWithMeanings) {
        AlertDialog.Builder(ctx)
            .setTitle("删除字词")
            .setMessage("确定删除「${item.word.term}」及其全部 ${item.meanings.size} 条释义？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                db.deleteWord(item.word.id)
                onDataChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}
