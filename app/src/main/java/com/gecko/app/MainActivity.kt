package com.gecko.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gecko.app.ui.GeckoRoot
import com.gecko.app.ui.GeckoViewModel
import com.gecko.app.ui.LocalAppContainer

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as GeckoApp
        setContent {
            val viewModel: GeckoViewModel = viewModel(factory = GeckoViewModel.factory(app))
            CompositionLocalProvider(LocalAppContainer provides app.container) {
                GeckoRoot(viewModel)
            }
        }
    }
}
