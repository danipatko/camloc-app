package com.dapa.camloc

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.dapa.camloc.activities.TrackerActivity
import com.dapa.camloc.databinding.ActivityMainBinding
import com.dapa.camloc.services.DiscoveryService
import com.dapa.camloc.services.NetworkService
import com.dapa.camloc.util.getNetworkInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var mNetworkService: NetworkService
    private var mBound: Boolean = false

    private lateinit var binding: ActivityMainBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        thread {
            ContextCompat.registerReceiver(this, discoveryBroadcastReceiver, IntentFilter(DiscoveryService.INTENT_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)

            startService(Intent(this, NetworkService::class.java))
            startService(Intent(this, DiscoveryService::class.java))
        }

        binding.deviceName.editText?.setText(Build.MODEL)

        // start tracker
        binding.launchButton.setOnClickListener {
            Intent(this@MainActivity, TrackerActivity::class.java).also {
                this@MainActivity.startActivity(it)
            }
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

    override fun onStart() {
        super.onStart()
        Intent(this, NetworkService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }

    override fun onResume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) checkNetwork()
        super.onResume()
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

    private val discoveryBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val name = intent.getStringExtra("name")!!
            val ip = intent.getStringExtra("ip")!!.drop(1)
            val port = intent.getIntExtra("port", -1)

            // Log.d(TAG, "got: $name | $ip:$port")

            binding.brokerText.text = ip
            binding.brokerStatus.text = "Found broker '$name' (port $port)"

            Toast.makeText(context, "got intent", Toast.LENGTH_SHORT).show()
        }
    }

    // service binding
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance.
            val binder = service as NetworkService.LocalBinder
            mNetworkService = binder.getService()
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    companion object {
        const val TAG = "CamlocMainActivity"
    }
}