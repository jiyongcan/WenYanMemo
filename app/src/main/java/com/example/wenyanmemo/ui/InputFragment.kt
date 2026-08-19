package com.example.wenyanmemo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.example.wenyanmemo.R
import com.example.wenyanmemo.databinding.FragmentInputBinding
import com.example.wenyanmemo.db.DatabaseHelper

class InputFragment : Fragment() {

    private var _binding: FragmentInputBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInputBinding.inflate(inflater, container, false)
        val db = DatabaseHelper.get(requireContext())

        val posOptions = resources.getStringArray(R.array.pos_options)
        binding.spPos.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, posOptions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

        binding.btnAdd.setOnClickListener { addMeaning() }

        // 在释义输入框按回车（IME 完成键）直接保存
        binding.etDef.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND) {
                addMeaning()
                true
            } else {
                false
            }
        }
        return binding.root
    }

    private fun addMeaning() {
        val db = DatabaseHelper.get(requireContext())
        val word = binding.etWord.text.toString().trim()
        val def = binding.etDef.text.toString().trim()
        if (word.isEmpty() || def.isEmpty()) {
            binding.tvStatus.text = "请先填写字词和释义"
            return
        }
        val pos = binding.spPos.selectedItem.toString()
        db.addMeaning(word, pos, def)
        binding.etDef.setText("")
        binding.tvStatus.text = "✔ 已为「$word」添加释义：$pos $def"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
