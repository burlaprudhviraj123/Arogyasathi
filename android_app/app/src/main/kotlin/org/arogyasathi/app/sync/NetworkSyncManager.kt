package org.arogyasathi.app.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.arogyasathi.app.data.ArogyasathiRepository
import org.arogyasathi.app.data.SyncResult

/**
 * Enterprise-grade Offline-to-Online Synchronizer.
 * Automatically detects cellular / Wi-Fi restoration and drains the local SQLite outbox queue.
 */
class NetworkSyncManager(
    private val context: Context,
    private val repository: ArogyasathiRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network restored: Syncing offline outbox queue to central Neon backend...")
            _isOnline.value = true
            scope.launch {
                val pending = repository.getPendingSyncCount()
                if (pending > 0) {
                    val res = repository.triggerSync()
                    _lastSyncResult.value = res
                    Log.d(TAG, "Sync complete: ${res.message}")
                }
            }
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: Reverting to 100% offline edge mode.")
            _isOnline.value = false
        }
    }

    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    fun stopMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback", e)
        }
    }

    private fun checkInitialConnectivity(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "NetworkSyncManager"
    }
}

