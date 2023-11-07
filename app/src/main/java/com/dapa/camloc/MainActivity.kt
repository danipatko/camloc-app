package com.dapa.camloc

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import com.dapa.camloc.events.BrokerInfo
import com.dapa.camloc.events.BrokerState
import com.dapa.camloc.events.StartTrackerActivity
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

    private var launched = false

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        EventBus.getDefault().register(this)

        thread {
            startService(Intent(this, DiscoveryService::class.java))
            startService(Intent(this, MQTTService::class.java))

            getIpInfo()
        }

        binding.launchButton.setOnClickListener {
            // start tracker
            val trackerIntent = Intent(this@MainActivity, TrackerActivity::class.java)
            this@MainActivity.startActivity(trackerIntent)
            launched = true
        }
    }

    override fun onResume() {
        launched = false
        super.onResume()
    }

    private fun getIpInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager: ConnectivityManager = super.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val linkProps = manager.getLinkProperties(manager.activeNetwork)

            if(linkProps != null) {
                gatewayIp = linkProps.dhcpServerAddress
                localIp = linkProps.linkAddresses.map { it.address }.first { it is Inet4Address }
            }

            if(gatewayIp == null)
                binding.brokerStatus.text = "No wifi connection"
        }

        runOnUiThread {
            binding.clientText.text = localIp?.hostAddress ?: "N/A"
            binding.gatewayText.text = gatewayIp?.hostAddress ?: "N/A"
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onStartTrackerActivity(nothing: StartTrackerActivity) {
        if(!launched) {
            val trackerIntent = Intent(this@MainActivity, TrackerActivity::class.java)
            this@MainActivity.startActivity(trackerIntent)
        }
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConnect(info: BrokerInfo) {
        binding.brokerText.text = info.display
        binding.brokerStatus.text = "Broker found, connecting..."
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBrokerState(ev: BrokerState) {
        binding.brokerStatus.text = if(ev.connected) "Connected" else "Lost broker connection"
    }

    override fun onStop() {
        EventBus.getDefault().unregister(this)
        return super.onStop()
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
        val TAG = "CamLocMainActivity"
    }
}