package com.gecko.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.gecko.app.di.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer não foi fornecido")
}
