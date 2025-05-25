package com.draco.ladb.utils

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

private const val TAG = "DNS"

class DnsDiscover private constructor(private val nsdManager: NsdManager) {
    private var started = false

    companion object {
        private var instance: DnsDiscover? = null
        var adbPort: Int? = null

        fun getInstance(nsdManager: NsdManager): DnsDiscover {
            return instance ?: DnsDiscover(nsdManager).also { instance = it }
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