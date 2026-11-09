package com.auris.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.auris.app.di.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer não foi fornecido")
}
