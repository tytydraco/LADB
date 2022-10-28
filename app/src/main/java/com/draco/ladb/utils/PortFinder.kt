package com.draco.ladb.utils

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class PortFinder(
    context: Context,
    private val portMode: AdbPortMode,
    private val onFound: (Int?) -> Unit,
) {
    companion object {
        enum class AdbPortMode(val serviceName: String) {
            ADB_CONNECT_PORT("_adb-tls-connect._tcp"),
            ADB_PAIRING_PORT("_adb-tls-pairing._tcp")
        }
    }

    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
    private val discoveryListener = DiscoveryListener()

    fun find() {
        nsdManager.discoverServices(portMode.serviceName, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stop() {
        nsdManager.stopServiceDiscovery(discoveryListener)
    }

    inner class DiscoveryListener : NsdManager.DiscoveryListener {
        private val resolveListener = ResolveListener()

        inner class ResolveListener : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                onFound(serviceInfo.port)
                stop()
            }
        }

        override fun onStartDiscoveryFailed(p0: String?, p1: Int) {}
        override fun onStopDiscoveryFailed(p0: String?, p1: Int) {}
        override fun onDiscoveryStarted(p0: String?) {}
        override fun onDiscoveryStopped(p0: String?) {}
        override fun onServiceLost(p0: NsdServiceInfo?) {}

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            nsdManager.resolveService(serviceInfo, resolveListener)
        }
    }
}