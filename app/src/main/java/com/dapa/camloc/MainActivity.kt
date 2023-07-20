package com.dapa.camloc

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
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

        thread {
            getIpInfo()
        }

        binding.launchButton.setOnClickListener {
            // start tracker
            val trackerIntent = Intent(this@MainActivity, TrackerActivity::class.java)
            this@MainActivity.startActivity(trackerIntent)
        }
    }

    override fun onStart() {
        super.onStart()
        // DEBUG
        // val trackerIntent = Intent(this@MainActivity, TrackerActivity::class.java)
        // this@MainActivity.startActivity(trackerIntent)
    }

    private fun getIpInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager: ConnectivityManager = super.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val linkProps = manager.getLinkProperties(manager.activeNetwork)

            if(linkProps != null) {
                gatewayIp = linkProps.dhcpServerAddress
                localIp = linkProps.linkAddresses.map { it.address }.first { it is Inet4Address }
            }
        }

        runOnUiThread {
            binding.clientText.text = localIp?.hostAddress ?: "N/A"
            binding.gatewayText.text = gatewayIp?.hostAddress ?: "N/A"
        }
    }

    companion object {
        val TAG = "CamLocMainActivity"
    }
}