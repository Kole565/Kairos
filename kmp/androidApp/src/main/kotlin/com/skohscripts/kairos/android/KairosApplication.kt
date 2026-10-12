package com.skohscripts.kairos.android

import android.app.Application

/** Application : pose le rapport de plantage local avant tout autre composant (activité, récepteurs). */
class KairosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KairosCrash.install(this)
    }
}
