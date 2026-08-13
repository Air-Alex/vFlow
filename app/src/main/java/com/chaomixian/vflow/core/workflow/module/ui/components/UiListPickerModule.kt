// 文件: java/com/chaomixian/vflow/core/workflow/module/ui/components/UiListPickerModule.kt
package com.chaomixian.vflow.core.workflow.module.ui.components

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElementType
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 列表选择（单选）组件
 *
 * 让用户从一个候选列表里挑一个值。底层使用 Material 3 下拉菜单
 * (MaterialAutoCompleteTextView)。
 *
 * 参数：
 * - key: 组件 ID，也是变量名（必填）
 * - label: 选择框上方的提示文案
 * - options: 候选项，按换行符分隔（每行一项）
 * - default_value: 默认选中的项（按内容匹配），留空则选中第一项
 * - trigger_event: 选中变化时是否触发事件
 *
 * 输出：
 * - id: 组件的唯一标识符
 *
 * 选中的值是一个字符串（候选项原文），可通过组件的 .value 访问。
 * 候选项可通过 .options 访问。
 *
 * 使用场景：
 * - 让用户在 Activity / 悬浮窗表单里从一组固定值中选一个
 * - 与 "当组件被操作" 模块配合实现选中即触发的逻辑
 */
class UiListPickerModule : BaseUiComponentModule() {
    override val id = "vflow.ui.component.listpicker"
    override val metadata = ActionMetadata(
        name = "列表选择",  // Fallback
        nameStringRes = R.string.module_vflow_ui_component_listpicker_name,
        description = "让用户从一组选项中挑选一个值。",  // Fallback
        descriptionStringRes = R.string.module_vflow_ui_component_listpicker_desc,
        iconRes = R.drawable.rounded_arrow_drop_down_24,
        category = "UI 组件",
        categoryId = "ui"
    )

    override fun getInputs() = listOf(
        InputDefinition(
            "key", "ID (变量名)",
            ParameterType.STRING,
            "picker1",
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_ui_component_listpicker_key_name
        ),
        InputDefinition(
            "label", "标签",
            ParameterType.STRING,
            "请选择",
            acceptsMagicVariable = true,
            nameStringRes = R.string.param_vflow_ui_component_listpicker_label_name
        ),
        InputDefinition(
            "options", "选项 (每行一项)",
            ParameterType.STRING,
            "选项 1\n选项 2\n选项 3",
            acceptsMagicVariable = true,
            nameStringRes = R.string.param_vflow_ui_component_listpicker_options_name
        ),
        InputDefinition(
            "default_value", "默认选中",
            ParameterType.STRING,
            "",
            acceptsMagicVariable = true,
            nameStringRes = R.string.param_vflow_ui_component_listpicker_default_value_name
        ),
        InputDefinition(
            "trigger_event", "选中时触发事件",
            ParameterType.BOOLEAN,
            true,
            nameStringRes = R.string.param_vflow_ui_component_listpicker_trigger_event_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence =
        PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_prefix_listpicker),
            PillUtil.createPillFromParam(step.parameters["key"], getInputs()[0])
        )

    override fun createUiElement(context: ExecutionContext, step: ActionStep): UiElement {
        val label = VariableResolver.resolve(step.parameters["label"]?.toString() ?: "", context)
        val optionsRaw = VariableResolver.resolve(step.parameters["options"]?.toString() ?: "", context)
        val defaultRaw = VariableResolver.resolve(step.parameters["default_value"]?.toString() ?: "", context)
        val key = step.parameters["key"]?.toString()?.takeIf { it.isNotEmpty() }
            ?: "picker_${System.currentTimeMillis()}"
        val trigger = step.parameters["trigger_event"] as? Boolean ?: true

        val options = parseOptions(optionsRaw)
        // 默认值：匹配用户显式给定的内容，否则取第一项；都没有则空字符串
        val resolvedDefault = when {
            defaultRaw.isNotEmpty() && options.contains(defaultRaw) -> defaultRaw
            options.isNotEmpty() -> options.first()
            else -> ""
        }

        return UiElement(
            id = key,
            type = UiElementType.LIST_PICKER,
            label = label,
            defaultValue = resolvedDefault,
            placeholder = "",
            isRequired = false,
            triggerEvent = trigger,
            options = options
        )
    }

    companion object {
        /**
         * 把多行文本解析成选项列表。
         * - 同时支持 "\n" 和 "\\n" 两种换行形式，兼容手输和序列化。
         * - 自动忽略空行和纯空白行。
         * - 去除每项首尾空白。
         */
        fun parseOptions(raw: String): List<String> {
            if (raw.isBlank()) return emptyList()
            return raw.replace("\\n", "\n")
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }
}
