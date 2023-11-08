package com.dapa.camloc

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
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

    private var mBound: Boolean = false
    private lateinit var mService: MQTTService

    private var gatewayIp: InetAddress? = null
    private var localIp: InetAddress? = null

    private var launched = false

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
                mService.mCameraConfig.set(positionX, positionY, rotation)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, MQTTService::class.java).also { intent ->
            intent.action = "main"
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        launched = false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MQTTService.ServiceBinder
            mService = binder.getService()
            mBound = true

            runOnUiThread {
                binding.positionX.editText?.setText(mService.mCameraConfig.positionX.toString())
                binding.positionY.editText?.setText(mService.mCameraConfig.positionY.toString())
                binding.rotation.editText?.setText(mService.mCameraConfig.rotation.toString())
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
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
        val txt = if(ev.connected) "Connected" else "Lost broker connection"
        Toast.makeText(this, txt, Toast.LENGTH_SHORT).show()
        binding.brokerStatus.text = txt
    }

    override fun onStop() {
        Log.d(TAG, "onStop called")

        unbindService(connection)
        mBound = false
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
        const val TAG = "CamLocMainActivity"
    }
}