package com.dapa.camloc

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.dapa.camloc.activities.TrackerActivity
import com.dapa.camloc.databinding.ActivityMainBinding
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var gatewayIp: InetAddress? = null
    private var localIp: InetAddress? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        load()
        thread {
            getIpInfo()
        }

        binding.launchButton.setOnClickListener {
            // start tracker
            val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
            val trackerIntent = Intent(this@MainActivity, TrackerActivity::class.java)
                .putExtra("posx", sharedPref.getFloat("posx", 0f))
                .putExtra("posy", sharedPref.getFloat("posy", 0f))
                .putExtra("rot", sharedPref.getFloat("rot", 0f))

            this@MainActivity.startActivity(trackerIntent)
        }
    }

    private fun getIpInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager: ConnectivityManager = super.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val linkProps = manager.getLinkProperties(manager.activeNetwork)
            gatewayIp = linkProps?.dhcpServerAddress
            localIp = linkProps?.linkAddresses?.map { it.address }?.first { it is Inet4Address }
        }

        runOnUiThread {
            binding.clientText.text = localIp?.hostAddress ?: "N/A"
            binding.gatewayText.text = gatewayIp?.hostAddress ?: "N/A"
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    // update shared preferences on focus change
                    save();
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun save() {
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            val values = mapOf("posx" to binding.posxField.editText?.text?.toString()?.toFloatOrNull(), "posy" to binding.posyField.editText?.text?.toString()?.toFloatOrNull(), "rot" to binding.rotField.editText?.text?.toString()?.toFloatOrNull())
            val disp = arrayOf(binding.posxField.editText, binding.posxField.editText, binding.posxField.editText)

            values.entries.forEachIndexed { i , entry ->
                if(entry.value == null) disp[i]?.error = "invalid float"
                else putFloat(entry.key, entry.value!!)
            }

            apply()
        }
    }

    private fun load() {
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
        binding.posxField.editText?.setText(sharedPref.getFloat("posx", 0f).toString())
        binding.posyField.editText?.setText(sharedPref.getFloat("posy", 0f).toString())
        binding.rotField.editText?.setText(sharedPref.getFloat("rot", 0f).toString())
    }

    companion object {
        val TAG = "CamLocMainActivity"
    }
}


