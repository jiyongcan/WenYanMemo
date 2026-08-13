package com.example.wenyanmemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.wenyanmemo.databinding.ActivityMainBinding
import com.example.wenyanmemo.ui.InputFragment
import com.example.wenyanmemo.ui.ReviewFragment
import com.example.wenyanmemo.ui.WordsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val inputFragment = InputFragment()
    private val wordsFragment = WordsFragment()
    private val reviewFragment = ReviewFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, reviewFragment, "review").hide(reviewFragment)
            .add(R.id.fragmentContainer, wordsFragment, "words").hide(wordsFragment)
            .add(R.id.fragmentContainer, inputFragment, "input")
            .commit()

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_input -> showFragment(inputFragment)
                R.id.nav_words -> { wordsFragment.refresh(); showFragment(wordsFragment) }
                R.id.nav_review -> showFragment(reviewFragment)
            }
            true
        }
    }

    private fun showFragment(target: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            listOf(inputFragment, wordsFragment, reviewFragment).forEach { f ->
                if (f === target) show(f) else hide(f)
            }
        }.commit()
    }
}
