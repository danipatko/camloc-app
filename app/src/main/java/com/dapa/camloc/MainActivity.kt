package com.dapa.camloc

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.dapa.camloc.activities.TrackerActivity
import com.dapa.camloc.databinding.ActivityMainBinding
import com.dapa.camloc.events.BrokerInfo
import com.dapa.camloc.services.DiscoveryService
import com.dapa.camloc.services.MQTTService
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
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
            EventBus.getDefault().register(this)

            startService(Intent(this, DiscoveryService::class.java))
            startService(Intent(this, MQTTService::class.java))

            getIpInfo()
        }

        binding.launchButton.setOnClickListener {
            // start tracker
            val trackerIntent = Intent(this@MainActivity, TrackerActivity::class.java)
            this@MainActivity.startActivity(trackerIntent)
        }
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConnect(info: BrokerInfo) {
        binding.brokerText.text = info.display
        Toast.makeText(this,  "Broker connected", Toast.LENGTH_SHORT).show()
    }

    companion object {
        val TAG = "CamLocMainActivity"
    }
}