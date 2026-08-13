package com.example.wenyanmemo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wenyanmemo.adapter.WordsAdapter
import com.example.wenyanmemo.databinding.FragmentWordsBinding
import com.example.wenyanmemo.db.DatabaseHelper
import com.example.wenyanmemo.export.DocxExporter
import com.example.wenyanmemo.model.WordWithMeanings

class WordsFragment : Fragment() {

    private var _binding: FragmentWordsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: DatabaseHelper
    private lateinit var adapter: WordsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWordsBinding.inflate(inflater, container, false)
        db = DatabaseHelper.get(requireContext())

        adapter = WordsAdapter(db) { refresh() }
        binding.rvWords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWords.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refresh() }
        })

        binding.btnExport.setOnClickListener { onExportClick() }
        refresh()
        return binding.root
    }

    /** 按当前搜索词刷新列表（切到本页时也会被调用） */
    fun refresh() {
        if (_binding == null) return
        val query = binding.etSearch.text.toString().trim()
        val items = db.search(query)
        adapter.submit(items)
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onExportClick() {
        val items = db.search("")
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "暂无数据可导出", Toast.LENGTH_SHORT).show()
            return
        }
        // Android 9 及以下需要存储权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE)
            return
        }
        doExport(items)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            doExport(db.search(""))
        } else {
            Toast.makeText(requireContext(), "未获得存储权限，无法导出", Toast.LENGTH_SHORT).show()
        }
    }

    private fun doExport(items: List<WordWithMeanings>) {
        val context = requireContext().applicationContext
        val activity = requireActivity()
        Thread {
            val uri = DocxExporter.export(context, items)
            activity.runOnUiThread {
                if (uri != null) {
                    Toast.makeText(context, "导出成功：已保存到「下载」目录", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "导出失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_STORAGE = 1001
    }
}