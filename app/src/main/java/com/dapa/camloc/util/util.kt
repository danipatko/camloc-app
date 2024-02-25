package com.dapa.camloc.util

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.net.Inet4Address

data class NetworkInfo(val address: Inet4Address, val gateway: Inet4Address)

@RequiresApi(Build.VERSION_CODES.R)
fun getNetworkInfo(context: Context): NetworkInfo? {
    val manager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val linkProps = manager.getLinkProperties(manager.activeNetwork) ?: return null
    val ip = linkProps.linkAddresses.map { it.address }.first { it is Inet4Address } as Inet4Address

    return NetworkInfo(ip, linkProps.dhcpServerAddress!!)
}


