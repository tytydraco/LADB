package com.draco.ladb.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

private const val TAG = "DNS"

class DnsDiscover private constructor(
    private val context: Context,
    private val nsdManager: NsdManager
) {
    private var started = false

    companion object {
        private var instance: DnsDiscover? = null
        var adbPort: Int? = null

        fun getInstance(context: Context, nsdManager: NsdManager): DnsDiscover {
            return instance ?: DnsDiscover(context, nsdManager).also { instance = it }
        }
    }

    fun scanAdbPorts() {
        if (started) {
            Log.w(TAG, "Already started")
            return
        }
        started = true
        nsdManager.discoverServices(
            "_adb-tls-connect._tcp",
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener
        )
    }


    fun getLocalIpAddress(): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

        // Ensure it's a valid Wi-Fi connection
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {

            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    val addresses = networkInterface.inetAddresses

                    while (addresses.hasMoreElements()) {
                        val inetAddress = addresses.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            return inetAddress.hostAddress
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "Service discovery: $service")
            Log.d(TAG, "Port: ${service.port}")

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Resolve failed: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Resolve successful: $serviceInfo")
                    Log.d(TAG, "Port: ${serviceInfo.port}")

                    val ipAddress = getLocalIpAddress()
                    Log.d("IP ADDRESS", ipAddress ?: "N/A")

                    val discoveredAddress = serviceInfo.host.hostAddress
                    if (discoveredAddress != ipAddress) {
                        Log.d(TAG, "IP does not match device")
                        return
                    }

                    adbPort = serviceInfo.port
                }
            }

            nsdManager.resolveService(service, resolveListener)
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.e(TAG, "Service lost: $service")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code:$errorCode")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code:$errorCode")
            nsdManager.stopServiceDiscovery(this)
        }
    }

}