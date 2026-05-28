package com.chaomixian.vflow.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.module.scripted.ModuleManager

class ExternalModulePackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val appContext = context.applicationContext
                ModuleRegistry.reset()
                ExternalModuleManager.reset()
                ModuleRegistry.initialize(appContext)
                ModuleManager.loadModules(appContext, force = true)
                ExternalModuleManager.loadModulesAsync(appContext, force = true)
            }
        }
    }
}
