package com.chaomixian.vflow.extension

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ExternalModuleCall private constructor(
    private val context: Context,
    private val connection: ServiceConnection,
    val provider: IVFlowExtensionProvider
) {
    fun close() {
        runCatching { context.unbindService(connection) }
    }

    companion object {
        suspend fun bind(context: Context, component: ComponentName): ExternalModuleCall? =
            suspendCancellableCoroutine { continuation ->
                val appContext = context.applicationContext
                var call: ExternalModuleCall? = null
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        val provider = IVFlowExtensionProvider.Stub.asInterface(service)
                        call = ExternalModuleCall(appContext, this, provider)
                        if (continuation.isActive) continuation.resume(call)
                    }

                    override fun onServiceDisconnected(name: ComponentName) = Unit

                    override fun onBindingDied(name: ComponentName) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onNullBinding(name: ComponentName) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                val bound = runCatching {
                    appContext.bindService(
                        Intent(ExternalModuleProtocol.ACTION_MODULE_PROVIDER).setComponent(component),
                        connection,
                        Context.BIND_AUTO_CREATE
                    )
                }.getOrDefault(false)
                if (!bound) continuation.resume(null)
                continuation.invokeOnCancellation {
                    call?.close() ?: runCatching { appContext.unbindService(connection) }
                }
            }
    }
}
