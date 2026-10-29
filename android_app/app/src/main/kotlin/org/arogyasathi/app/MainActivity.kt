package org.arogyasathi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.arogyasathi.app.data.ArogyasathiRepository
import org.arogyasathi.app.sync.NetworkSyncManager
import org.arogyasathi.app.ui.ArogyasathiTheme
import org.arogyasathi.app.ui.MainScreen

class MainActivity : ComponentActivity() {

    private lateinit var syncManager: NetworkSyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = ArogyasathiRepository(applicationContext)
        syncManager = NetworkSyncManager(applicationContext, repository)
        syncManager.startMonitoring()

        setContent {
            ArogyasathiTheme {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::syncManager.isInitialized) {
            syncManager.stopMonitoring()
        }
    }
}

