package com.af010.creator.docreader.app

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class NetworkConnectivityObserver(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    enum class Status {
        Available, Unavailable, Lost
    }

    enum class NetworkType {
        WIFI, CELLULAR, ETHERNET, VPN, NONE
    }

    fun observe(): Flow<Status> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Status.Available)
            }

            override fun onLost(network: Network) {
                trySend(Status.Lost)
            }

            override fun onUnavailable() {
                trySend(Status.Unavailable)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)
        trySend(getCurrentStatus())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    fun getCurrentStatus(): Status {
        val network = connectivityManager.activeNetwork ?: return Status.Unavailable
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return Status.Unavailable

        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            Status.Available
        } else {
            Status.Unavailable
        }
    }

    fun getNetworkType(): NetworkType {
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            else -> NetworkType.NONE
        }
    }

    fun isNetworkAvailable(): Boolean = getCurrentStatus() == Status.Available

    fun isWifi(): Boolean = getNetworkType() == NetworkType.WIFI

    fun isCellular(): Boolean = getNetworkType() == NetworkType.CELLULAR
}

// ==================== DSL Builder ====================

class NetworkObserverBuilder {
    private var onAvailable: (() -> Unit)? = null
    private var onLost: (() -> Unit)? = null
    private var onReconnected: (() -> Unit)? = null
    private var onUnavailable: (() -> Unit)? = null

    fun onAvailable(block: () -> Unit) {
        onAvailable = block
    }

    fun onLost(block: () -> Unit) {
        onLost = block
    }

    fun onReconnected(block: () -> Unit) {
        onReconnected = block
    }

    fun onUnavailable(block: () -> Unit) {
        onUnavailable = block
    }

    internal fun build() = NetworkCallbacks(onAvailable, onLost, onReconnected, onUnavailable)
}

internal data class NetworkCallbacks(
    val onAvailable: (() -> Unit)?,
    val onLost: (() -> Unit)?,
    val onReconnected: (() -> Unit)?,
    val onUnavailable: (() -> Unit)?
)

// ==================== Context Extensions ====================

/**
 * Quick check if network is available
 */
fun Context.isNetworkAvailable(): Boolean {
    return NetworkConnectivityObserver(this).isNetworkAvailable()
}

/**
 * Get current network type
 */
fun Context.getNetworkType(): NetworkConnectivityObserver.NetworkType {
    return NetworkConnectivityObserver(this).getNetworkType()
}

/**
 * Check if connected via WiFi
 */
fun Context.isWifiConnected(): Boolean {
    return NetworkConnectivityObserver(this).isWifi()
}

/**
 * Check if connected via Cellular
 */
fun Context.isCellularConnected(): Boolean {
    return NetworkConnectivityObserver(this).isCellular()
}
fun Context.openWifiSettings() {
    try {
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    } catch (e: Exception) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

// ==================== Lifecycle-aware Extensions ====================

/**
 * Observe network connectivity with DSL in Activity
 *
 * Example:
 * ```
 * observeNetwork {
 *     onAvailable { loadData() }
 *     onLost { showOfflineBar() }
 *     onReconnected { refreshData() }
 * }
 * ```
 */
fun AppCompatActivity.observeNetwork(builder: NetworkObserverBuilder.() -> Unit) {
    val callbacks = NetworkObserverBuilder().apply(builder).build()
    observeNetworkInternal(this, this, callbacks)
}

/**
 * Observe network connectivity with DSL in Fragment
 */
fun Fragment.observeNetwork(builder: NetworkObserverBuilder.() -> Unit) {
    val ctx = context ?: return
    val callbacks = NetworkObserverBuilder().apply(builder).build()
    observeNetworkInternal(ctx, viewLifecycleOwner, callbacks)
}

/**
 * Simple network observation in Activity
 */
fun ComponentActivity.observeNetworkConnectivity(
    onNetworkAvailable: (() -> Unit)? = null,
    onNetworkLost: (() -> Unit)? = null,
    onNetworkReconnected: (() -> Unit)? = null
) {
    val callbacks = NetworkCallbacks(onNetworkAvailable, onNetworkLost, onNetworkReconnected, null)
    observeNetworkInternal(this, this, callbacks)
}

/**
 * Simple network observation in Fragment
 */
fun Fragment.observeNetworkConnectivity(
    onNetworkAvailable: (() -> Unit)? = null,
    onNetworkLost: (() -> Unit)? = null,
    onNetworkReconnected: (() -> Unit)? = null
) {
    val ctx = context ?: return
    val callbacks = NetworkCallbacks(onNetworkAvailable, onNetworkLost, onNetworkReconnected, null)
    observeNetworkInternal(ctx, viewLifecycleOwner, callbacks)
}

// ==================== Internal Implementation ====================

private fun observeNetworkInternal(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    callbacks: NetworkCallbacks
) {
    val networkObserver = NetworkConnectivityObserver(context)
    var wasDisconnected = false
    var isFirstEmission = true

    lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            networkObserver.observe().collect { status ->
                when (status) {
                    NetworkConnectivityObserver.Status.Available -> {
                        if (wasDisconnected && !isFirstEmission) {
                            callbacks.onReconnected?.invoke()
                        }
                        wasDisconnected = false
                        callbacks.onAvailable?.invoke()
                    }

                    NetworkConnectivityObserver.Status.Lost -> {
                        wasDisconnected = true
                        callbacks.onLost?.invoke()
                    }

                    NetworkConnectivityObserver.Status.Unavailable -> {
                        wasDisconnected = true
                        callbacks.onUnavailable?.invoke()
                        callbacks.onLost?.invoke()
                    }
                }
                isFirstEmission = false
            }
        }
    }
}
