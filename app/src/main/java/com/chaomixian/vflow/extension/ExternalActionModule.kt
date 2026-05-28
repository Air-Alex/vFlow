package com.chaomixian.vflow.extension

import android.content.Context
import android.os.Bundle
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleCategories
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

class ExternalActionModule(
    private val providerManifest: ProviderManifest,
    private val spec: ExternalModuleSpec
) : BaseModule() {
    override val id: String = spec.moduleId

    override val metadata: ActionMetadata = ActionMetadata(
        name = spec.name,
        description = spec.description,
        iconRes = R.drawable.rounded_extension_24,
        category = ModuleCategories.EXTERNAL_EXTENSION,
        categoryId = ModuleCategories.EXTERNAL_EXTENSION
    )

    override fun getInputs(): List<InputDefinition> = spec.inputs.map { it.toInputDefinition() }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> =
        spec.outputs.map { it.toOutputDefinition() }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val firstInput = getInputs().firstOrNull { !it.isHidden }
        return if (firstInput == null) {
            spec.name
        } else {
            val pill = PillUtil.createPillFromParam(step.parameters[firstInput.id], firstInput)
            PillUtil.buildSpannable(context, "${spec.name} ", pill)
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val component = ExternalModuleManager.resolveProviderComponent(id)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_external_module_unavailable),
                appContext.getString(R.string.error_vflow_external_module_missing, spec.name)
            )

        onProgress(ProgressUpdate("正在调用外部扩展: ${providerManifest.providerName}"))

        val request = Bundle().apply {
            putString(ExternalModuleProtocol.KEY_REQUEST_ID, UUID.randomUUID().toString())
            putString(ExternalModuleProtocol.KEY_MODULE_ID, id)
            putLong(ExternalModuleProtocol.KEY_TIMEOUT_MS, 30000L)
            putString(ExternalModuleProtocol.KEY_LOCALE, Locale.getDefault().toLanguageTag())
            putString(
                ExternalModuleProtocol.KEY_PARAMETERS_JSON,
                ExternalModuleProtocol.parametersJson(getInputs().associate { input ->
                    input.id to context.getVariable(input.id).raw
                })
            )
        }

        val service = withTimeoutOrNull(3000L) {
            ExternalModuleCall.bind(context.applicationContext, component)
        } ?: return ExecutionResult.Failure(
            appContext.getString(R.string.error_vflow_external_module_unavailable),
            appContext.getString(R.string.error_vflow_external_module_connect_failed, providerManifest.providerName)
        )

        return try {
            val response = withTimeoutOrNull(35000L) {
                withContext(Dispatchers.IO) { service.provider.executeModule(request) }
            } ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_external_module_timeout_title),
                appContext.getString(R.string.error_vflow_external_module_timeout, providerManifest.providerName)
            )

            when (response.getString(ExternalModuleProtocol.KEY_STATUS)) {
                ExternalModuleProtocol.STATUS_SUCCESS -> {
                    val outputs = ExternalModuleProtocol.parseMap(
                        response.getString(ExternalModuleProtocol.KEY_OUTPUTS_JSON)
                    ).mapValues { (_, value) -> VObjectFactory.from(value) }
                    ExecutionResult.Success(outputs)
                }
                else -> ExecutionResult.Failure(
                    response.getString(ExternalModuleProtocol.KEY_ERROR_TITLE)
                        ?: appContext.getString(R.string.error_vflow_external_module_failed_title),
                    response.getString(ExternalModuleProtocol.KEY_ERROR_MESSAGE)
                        ?: appContext.getString(R.string.error_vflow_external_module_failed)
                )
            }
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_external_module_exception_title),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_external_module_unknown)
            )
        } finally {
            service.close()
        }
    }
}
