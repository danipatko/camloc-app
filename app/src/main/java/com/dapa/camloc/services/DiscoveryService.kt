package com.dapa.camloc.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import java.net.Inet4Address

class DiscoveryService : Service() {
    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "service discovered: name=${service.serviceName} | type=${service.serviceType}")
            nsdManager!!.resolveService(service, ResolveListener())
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.e(TAG, "service lost: $service")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "Discovery stopped for $serviceType")
        }

        // unhandled
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

    inner class ResolveListener : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Log.e(TAG, "Resolve failed ($errorCode) for $serviceInfo")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "connecting to broker on $serviceInfo")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val wifi = this.getSystemService(Context.WIFI_SERVICE) as WifiManager

        multicastLock = wifi.createMulticastLock(TAG)
        multicastLock!!.setReferenceCounted(true)
        multicastLock!!.acquire()

        nsdManager = (this.getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            discoverServices(REG_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        multicastLock?.release()
        nsdManager?.stopServiceDiscovery(discoveryListener)
        return super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val TAG = "CamlocDiscoveryService"
        // DNS SRV record to search for
        const val REG_TYPE = "_camloc._tcp"
    }
}