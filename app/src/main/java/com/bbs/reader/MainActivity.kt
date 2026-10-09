package com.bbs.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbs.reader.data.model.AppSettings
import com.bbs.reader.ui.navigation.AppNav
import com.bbs.reader.ui.theme.BbsReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = LocalContext.current.applicationContext as BbsApp
            val settingsStore = remember { app.settingsStore }
            val settings by settingsStore.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            BbsReaderTheme(themeMode = settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav()
                }
            }
        }
    }
}
