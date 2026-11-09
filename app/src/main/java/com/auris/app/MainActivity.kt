package com.auris.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auris.app.ui.AurisRoot
import com.auris.app.ui.AurisViewModel
import com.auris.app.ui.LocalAppContainer

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AurisApp
        setContent {
            val viewModel: AurisViewModel = viewModel(factory = AurisViewModel.factory(app))
            CompositionLocalProvider(LocalAppContainer provides app.container) {
                AurisRoot(viewModel)
            }
        }
    }
}
