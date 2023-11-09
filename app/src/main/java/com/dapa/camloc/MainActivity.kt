package com.dapa.camloc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.dapa.camloc.activities.TrackerActivity
import com.dapa.camloc.databinding.ActivityMainBinding
import com.dapa.camloc.events.*
import com.dapa.camloc.services.DiscoveryService
import com.dapa.camloc.services.MQTTService
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var mBound: Boolean = false
    private lateinit var mService: MQTTService

    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        thread {
            startService(Intent(this, DiscoveryService::class.java))
            startService(Intent(this, MQTTService::class.java))
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
        EventBus.getDefault().register(this)
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

            setConfigText()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    // updates ip stuff
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onIpInfo(info: IpInfo) {
        runOnUiThread {
            if(info.gateway == null) {
                binding.brokerStatus.text = getString(R.string.nowifi)
            }
            binding.clientText.text = info.local?.hostAddress ?: getString(R.string.na)
            binding.gatewayText.text = info.gateway?.hostAddress ?: getString(R.string.na)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onStartTrackerActivity(nothing: StartTrackerActivity) {
        if(!launched) {
            val trackerIntent = Intent(this@MainActivity, TrackerActivity::class.java)
            this@MainActivity.startActivity(trackerIntent)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBrokerFound(info: BrokerInfo) {
        binding.brokerText.text = info.display
        binding.brokerStatus.text = getString(R.string.broker_found)
    }

    // on connection or connect failure
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBrokerState(ev: BrokerState) {
        val txt = if(ev.isConnected) "Connected" else "Lost broker connection"
        Toast.makeText(this, txt, Toast.LENGTH_SHORT).show()
        binding.brokerStatus.text = txt
    }

    // set text if remote changed config
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConfigChanged(ev: ConfigChanged) {
         setConfigText()
         Toast.makeText(this, "config changed by remote", Toast.LENGTH_SHORT).show()
    }

    fun setConfigText() {
        if(mBound) {
            runOnUiThread {
                binding.positionX.editText?.setText(mService.mCameraConfig.positionX.toString())
                binding.positionY.editText?.setText(mService.mCameraConfig.positionY.toString())
                binding.rotation.editText?.setText(mService.mCameraConfig.rotation.toString())
            }
        }
    }

    override fun onStop() {
        super.onStop()

        unbindService(connection)
        mBound = false
        EventBus.getDefault().unregister(this)
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