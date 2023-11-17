package com.dapa.camloc.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.dapa.camloc.events.BrokerInfo
import com.dapa.camloc.events.IpInfo
import org.greenrobot.eventbus.EventBus
import java.net.Inet4Address
import java.net.InetAddress

class DiscoveryService : Service() {
    private var nsdManager: NsdManager? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getIpInfo()

        val wifi = this.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("multicastLock")
        multicastLock!!.setReferenceCounted(true)
        multicastLock!!.acquire()

        nsdManager = (this.getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }

        return START_STICKY
    }

    private fun getIpInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager: ConnectivityManager = super.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val linkProps = manager.getLinkProperties(manager.activeNetwork)

            if(linkProps != null) {
                val gatewayIp = linkProps.dhcpServerAddress
                val localIp = linkProps.linkAddresses.map { it.address }.first { it is Inet4Address } as Inet4Address
                EventBus.getDefault().post(IpInfo(localIp, gatewayIp))
            } else {
                EventBus.getDefault().post(IpInfo(null, null))
            }
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "Service discovery success\nname: ${service.serviceName} | type: ${service.serviceType} | host: ${service.host}:${service.port}")

            // for whatever reason, android does not know the
            // difference between service type and name
            // hardcoding these 2 values moreover solve the problem
            // however, if there are more than one mdns publishers
            // with mqtt type, undefined behaviour is guaranteed

            if(!service.serviceName.contains("mqtt", true)) {
                return
            }

            service.serviceType = MDNS_SERVICE_TYPE
            service.serviceName = MDNS_SERVICE_NAME

            nsdManager?.resolveService(service, ResolveListener())
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            // When the network service is no longer available.
            // Internal bookkeeping code goes here.
            Log.e(TAG, "service lost: $service")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code:$errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code:$errorCode")
            nsdManager?.stopServiceDiscovery(this)
        }
    }

    private class ResolveListener: NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Called when the resolve fails. Use the error code to debug.
            Log.e(TAG, "Resolve failed ($errorCode) for $serviceInfo")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "connecting to MQTT broker on $serviceInfo")
            EventBus.getDefault().post(BrokerInfo(serviceInfo.host, serviceInfo.port))
        }
    }

    override fun onDestroy() {
        multicastLock?.release()
        nsdManager?.stopServiceDiscovery(discoveryListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val TAG = "CamLocDiscoveryService"
        const val NSD_SERVICE_TYPE = "_services._dns-sd._udp"
        const val MDNS_SERVICE_NAME = "_camloc"
        const val MDNS_SERVICE_TYPE = "_mqtt._tcp"
    }
}