package com.dapa.camloc

import com.google.android.material.color.DynamicColors

class Application : android.app.Application(){
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}