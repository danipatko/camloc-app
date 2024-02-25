package com.dapa.camloc

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.dapa.camloc.activities.TrackerActivity
import com.dapa.camloc.databinding.ActivityMainBinding
import com.dapa.camloc.services.DiscoveryService
import com.dapa.camloc.util.getNetworkInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var mBound: Boolean = false
    private var launched = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        thread {
            startService(Intent(this, DiscoveryService::class.java))
        }

        binding.deviceName.editText?.setText(Build.MODEL)

        // start tracker
        binding.launchButton.setOnClickListener {
            Intent(this@MainActivity, TrackerActivity::class.java).also {
                this@MainActivity.startActivity(it)
            }
            launched = true
        }

        binding.saveConfigButton.setOnClickListener {
            if(mBound) {
                val positionX = binding.positionX.editText?.text.toString().toFloat()
                val positionY = binding.positionY.editText?.text.toString().toFloat()
                val rotation = binding.rotation.editText?.text.toString().toFloat()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkNetwork() {
        val info = getNetworkInfo(this)
        if(info == null) {
            MaterialAlertDialogBuilder(this).apply {
                setTitle("Not connected")
                setMessage("Turn on wifi and connect to a network!")
                setPositiveButton("Kay") { _, _ ->
                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS));
                }
                setNegativeButton("Nah") { _, _ ->
                    finish()
                    exitProcess(0)
                }
            }.create().show()
        } else {
            binding.clientText.text = info.address.hostAddress
            binding.gatewayText.text = info.gateway.hostAddress
        }
    }

    override fun onResume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) checkNetwork()
        super.onResume()
        launched = false
    }

    // focus fix
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        currentFocus?.apply {
            if (this is EditText) {
                clearFocus()
            }
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        }
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        const val TAG = "CamlocMainActivity"
    }
}