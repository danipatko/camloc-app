package com.dapa.camloc

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.dapa.camloc.activities.TrackerActivity
import com.dapa.camloc.databinding.ActivityMainBinding
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var gatewayIp: InetAddress
    private lateinit var localIp: InetAddress

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Note: there were a whole lot of API changes in getting network/interface information
        // there is no way I'm going to implement all of them (might aswell increment minsdk to 30)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            thread {
                getIpInfo()
            }
        }

        binding.launchButton.setOnClickListener {
            // start tracker
            val myIntent = Intent(this@MainActivity, TrackerActivity::class.java)
            this@MainActivity.startActivity(myIntent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getIpInfo() {
        val manager: ConnectivityManager = super.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProps = manager.getLinkProperties(manager.activeNetwork)!!

        gatewayIp = linkProps.dhcpServerAddress!!
        localIp = linkProps.linkAddresses.map { it.address }.first { it is Inet4Address }

        runOnUiThread {
            binding.clientText.text = localIp.hostAddress
            binding.gatewayText.text = gatewayIp.hostAddress
        }
    }

    companion object {
        val TAG = "CamLocMainActivity"
    }
}