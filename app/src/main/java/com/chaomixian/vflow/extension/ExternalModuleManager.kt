package com.chaomixian.vflow.extension

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.IBinder
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ModuleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object ExternalModuleManager {
    private const val TAG = "ExternalModuleManager"
    private const val PREFS_NAME = "external_module_providers"
    private const val KEY_KNOWN_PROVIDERS = "known_providers"
    private const val LOAD_TIMEOUT_MS = 3000L

    private val providerComponents = mutableMapOf<String, ComponentName>()
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val loadMutex = Mutex()
    private var hasLoaded = false

    suspend fun loadModules(context: Context, force: Boolean = false) {
        loadMutex.withLock {
            if (hasLoaded && !force) return
            if (force) providerComponents.clear()
            val appContext = context.applicationContext
            val providers = discoverProviders(appContext)
            coroutineScope {
                providers.map { component ->
                    async(Dispatchers.IO) { loadProvider(appContext, component) }
                }.awaitAll()
            }
            hasLoaded = true
        }
    }

    fun loadModulesAsync(context: Context, force: Boolean = false) {
        managerScope.launch {
            loadModules(context, force)
        }
    }

    fun resolveProviderComponent(moduleId: String): ComponentName? = providerComponents[moduleId]

    fun reset() {
        providerComponents.clear()
        hasLoaded = false
    }

    private fun discoverProviders(context: Context): List<ComponentName> {
        val intent = Intent(ExternalModuleProtocol.ACTION_MODULE_PROVIDER)
        val services: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)
        }
        return services.mapNotNull { info ->
            val serviceInfo = info.serviceInfo ?: return@mapNotNull null
            val apiVersion = serviceInfo.metaData?.getInt(ExternalModuleProtocol.META_API_VERSION)
                ?: ExternalModuleProtocol.API_VERSION
            if (apiVersion > ExternalModuleProtocol.API_VERSION) return@mapNotNull null
            ComponentName(serviceInfo.packageName, serviceInfo.name)
        }
    }

    private suspend fun loadProvider(context: Context, component: ComponentName) {
        val service = withTimeoutOrNull(LOAD_TIMEOUT_MS) { bindProvider(context, component) }
        if (service == null) {
            DebugLogger.w(TAG, "Provider bind timed out: $component")
            return
        }

        try {
            val manifest = withContext(Dispatchers.IO) {
                ExternalModuleProtocol.parseManifest(service.getProviderManifest())
            }
            markKnownProviderEnabled(context, component.packageName)
            if (!isProviderEnabled(context, component.packageName)) return
            manifest.modules.forEach { spec ->
                providerComponents[spec.moduleId] = component
                if (ModuleRegistry.getModule(spec.moduleId) == null) {
                    ModuleRegistry.register(ExternalActionModule(manifest, spec), context)
                }
                DebugLogger.d(TAG, "Loaded external module ${spec.moduleId} from $component")
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to load provider $component", e)
        } finally {
            runCatching { context.unbindService(activeConnections.remove(component) ?: return) }
        }
    }

    private val activeConnections = mutableMapOf<ComponentName, ServiceConnection>()

    private suspend fun bindProvider(
        context: Context,
        component: ComponentName
    ): IVFlowExtensionProvider? = suspendCancellableCoroutine { continuation ->
        val intent = Intent(ExternalModuleProtocol.ACTION_MODULE_PROVIDER).setComponent(component)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (continuation.isActive) {
                    continuation.resume(IVFlowExtensionProvider.Stub.asInterface(service))
                }
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit

            override fun onBindingDied(name: ComponentName) {
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onNullBinding(name: ComponentName) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
        activeConnections[component] = connection
        val bound = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) {
            activeConnections.remove(component)
            continuation.resume(null)
        }
        continuation.invokeOnCancellation {
            activeConnections.remove(component)?.let { runCatching { context.unbindService(it) } }
        }
    }

    private fun markKnownProviderEnabled(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val known = prefs.getStringSet(KEY_KNOWN_PROVIDERS, emptySet()).orEmpty().toMutableSet()
        if (known.add(packageName)) {
            prefs.edit()
                .putStringSet(KEY_KNOWN_PROVIDERS, known)
                .putBoolean(providerEnabledKey(packageName), true)
                .apply()
        }
    }

    private fun isProviderEnabled(context: Context, packageName: String): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(providerEnabledKey(packageName), false)

    private fun providerEnabledKey(packageName: String) = "provider_enabled_$packageName"
}
