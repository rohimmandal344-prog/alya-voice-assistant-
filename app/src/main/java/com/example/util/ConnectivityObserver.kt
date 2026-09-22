package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * ConnectivityObserver
 * 
 * Monitors the network connection status using ConnectivityManager.NetworkCallback.
 * Returns a Flow of Status indicating if the device is Available or Unavailable.
 */
class ConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    enum class Status {
        Available, Unavailable, Losing, Lost
    }

    fun observe(): Flow<Status> {
        return callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    launch { send(Status.Available) }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    launch { send(if (hasInternet) Status.Available else Status.Unavailable) }
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    super.onLosing(network, maxMsToLive)
                    launch { send(Status.Losing) }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    // Check if default active network is truly offline or switched to cellular/wifi
                    if (!isOnline()) {
                        launch { send(Status.Lost) }
                    } else {
                        launch { send(Status.Available) }
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    if (!isOnline()) {
                        launch { send(Status.Unavailable) }
                    } else {
                        launch { send(Status.Available) }
                    }
                }
            }

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    connectivityManager.registerDefaultNetworkCallback(callback)
                } else {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    connectivityManager.registerNetworkCallback(request, callback)
                }
            } catch (e: Exception) {
                // Fallback for restricted environments
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }

            // Initial check
            val isCurrentlyOnline = isOnline()
            if (isCurrentlyOnline) {
                launch { send(Status.Available) }
            } else {
                launch { send(Status.Unavailable) }
            }

            awaitClose {
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (_: Exception) {}
            }
        }.distinctUntilChanged()
    }
    
    /**
     * Synchronous check for current network status.
     */
    fun isOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
