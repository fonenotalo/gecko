package com.gecko.app

import android.app.Application
import com.gecko.app.di.AppContainer

class GeckoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
