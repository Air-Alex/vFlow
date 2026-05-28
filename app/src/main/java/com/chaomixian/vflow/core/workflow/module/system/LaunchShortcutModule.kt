package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.directToolMetadata
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class LaunchShortcutModule : BaseModule() {

    companion object {
        private const val MODE_AUTO = "auto"
        private const val MODE_SHIZUKU = "shizuku"
        private const val MODE_ROOT = "root"
        private const val MODE_CORE = "core"
        private const val MODE_CORE_ROOT = "core_root"
        private val MODE_OPTIONS = listOf(MODE_AUTO, MODE_SHIZUKU, MODE_ROOT, MODE_CORE, MODE_CORE_ROOT)
    }

    override val id = "vflow.system.launch_shortcut"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_launch_shortcut_name,
        descriptionStringRes = R.string.module_vflow_system_launch_shortcut_desc,
        name = "启动快捷方式",
        description = "通过 Shell 启动一个应用快捷方式。",
        iconRes = R.drawable.rounded_activity_zone_24,
        category = "应用与系统",
        categoryId = "device"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Launch an app shortcut selected from the shortcut picker. This relies on shell access because Android does not expose a general public shortcut picker for normal apps.",
        workflowStepDescription = "Launch an app shortcut.",
        inputHints = mapOf(
            "shortcutLabel" to "Display label of the selected shortcut.",
            "packageName" to "Android package that owns the selected shortcut.",
            "launchCommand" to "Resolved shell launch command for the shortcut.",
            "mode" to "Shell privilege mode. Leave auto unless the user explicitly requires Shizuku, root, Core, or Core Root."
        ),
        requiredInputIds = setOf("launchCommand")
    )

    override val uiProvider: ModuleUIProvider = LaunchShortcutUIProvider()

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val mode = step?.parameters?.get("mode") as? String ?: MODE_AUTO
        return when (mode) {
            MODE_ROOT -> listOf(PermissionManager.ROOT)
            MODE_SHIZUKU -> listOf(PermissionManager.SHIZUKU)
            MODE_CORE -> listOf(PermissionManager.CORE)
            MODE_CORE_ROOT -> listOf(PermissionManager.CORE_ROOT)
            else -> ShellManager.getRequiredPermissions(LogManager.applicationContext)
        }
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "shortcutLabel",
            name = "快捷方式名称",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_system_launch_shortcut_label_name
        ),
        InputDefinition(
            id = "packageName",
            name = "应用包名",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_system_launch_shortcut_package_name
        ),
        InputDefinition(
            id = "launchCommand",
            name = "启动命令",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            isHidden = true,
            nameStringRes = R.string.param_vflow_system_launch_shortcut_command_name
        ),
        InputDefinition(
            id = "mode",
            name = "执行方式",
            staticType = ParameterType.ENUM,
            defaultValue = MODE_AUTO,
            options = MODE_OPTIONS,
            acceptsMagicVariable = false,
            isFolded = true,
            nameStringRes = R.string.param_vflow_system_launch_shortcut_mode_name,
            optionsStringRes = listOf(
                R.string.option_vflow_shizuku_shell_command_mode_auto,
                R.string.option_vflow_shizuku_shell_command_mode_shizuku,
                R.string.option_vflow_shizuku_shell_command_mode_root,
                R.string.option_vflow_shizuku_shell_command_mode_core,
                R.string.option_vflow_shizuku_shell_command_mode_core_root
            )
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_launch_shortcut_success_name
        ),
        OutputDefinition(
            id = "result",
            name = "执行结果",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_system_launch_shortcut_result_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val shortcutLabel = step.parameters["shortcutLabel"] as? String
        val packageName = step.parameters["packageName"] as? String
        if (shortcutLabel.isNullOrBlank()) {
            return context.getString(R.string.summary_vflow_system_launch_shortcut_select)
        }

        val label = if (packageName.isNullOrBlank()) shortcutLabel else "$shortcutLabel · $packageName"
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_system_launch_shortcut_prefix) + " ",
            PillUtil.Pill(label, "shortcutLabel")
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val launchCommand = context.getVariableAsString("launchCommand", "")
        if (launchCommand.isBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_shortcut_missing_command),
                appContext.getString(R.string.error_vflow_system_launch_shortcut_select_first)
            )
        }

        val modeValue = context.getVariableAsString("mode", MODE_AUTO)
        val mode = when (modeValue) {
            MODE_ROOT -> ShellManager.ShellMode.ROOT
            MODE_SHIZUKU -> ShellManager.ShellMode.SHIZUKU
            MODE_CORE -> ShellManager.ShellMode.CORE
            MODE_CORE_ROOT -> ShellManager.ShellMode.CORE_ROOT
            else -> ShellManager.ShellMode.AUTO
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_launch_shortcut_running)))
        val result = ShellManager.execShellCommandWithResult(context.applicationContext, launchCommand, mode)

        return if (result.success) {
            ExecutionResult.Success(
                mapOf(
                    "success" to VBoolean(true),
                    "result" to VString(result.output)
                )
            )
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_launch_shortcut_failed),
                result.output.removePrefix("Error: ").trim()
            )
        }
    }
}
