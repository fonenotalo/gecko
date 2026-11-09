package com.auris.app

import android.app.Application
import com.auris.app.di.AppContainer

class AurisApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
