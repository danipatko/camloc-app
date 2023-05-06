package com.dapa.camloc

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dapa.camloc.activities.TrackerActivity
import com.dapa.camloc.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // start tracker
        val myIntent = Intent(this@MainActivity, TrackerActivity::class.java)
        this@MainActivity.startActivity(myIntent)
    }
}