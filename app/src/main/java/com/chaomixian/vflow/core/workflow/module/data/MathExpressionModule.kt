package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.AiModuleMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.AiModuleUsageScope
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.parser.VariablePathParser
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.notkamui.keval.Keval

class MathExpressionModule : BaseModule() {
    companion object {
        private const val INPUT_EXPRESSION = "expression"
        private const val INPUT_ASSIGN_TO = "assignTo"
        private const val OUTPUT_RESULT = "result"
    }

    override val id = "vflow.data.math_expression"

    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_math_expression_name,
        descriptionStringRes = R.string.module_vflow_data_math_expression_desc,
        name = "数学表达式",
        description = "使用 Keval 计算数学表达式",
        iconRes = R.drawable.rounded_calculate_24,
        category = "数据",
        categoryId = "data"
    )

    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        workflowStepDescription = "Evaluate a mathematical expression with Keval. Supports operators like +, -, *, /, %, ^, functions like sqrt, abs, min, max, and constants PI and e.",
        inputHints = mapOf(
            INPUT_EXPRESSION to "A math expression. It may contain rich-text variable references that resolve to numeric text before evaluation.",
            INPUT_ASSIGN_TO to "Optional named/global variable reference to receive the result."
        ),
        requiredInputIds = setOf(INPUT_EXPRESSION)
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = INPUT_EXPRESSION,
            nameStringRes = R.string.param_vflow_data_math_expression_expression_name,
            name = "表达式",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true,
            hintStringRes = R.string.hint_vflow_data_math_expression_expression
        ),
        InputDefinition(
            id = INPUT_ASSIGN_TO,
            nameStringRes = R.string.param_vflow_data_math_expression_assign_to_name,
            name = "赋值给",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false,
            acceptsNamedVariable = true,
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?) = listOf(
        OutputDefinition(
            id = OUTPUT_RESULT,
            name = "结果",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_data_math_expression_result_name
        )
    )

    override fun createSteps(): List<ActionStep> = listOf(ActionStep(moduleId = id, parameters = emptyMap()))

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val expression = step.parameters[INPUT_EXPRESSION]?.toString().orEmpty()
        return if (expression.isBlank()) {
            ValidationResult(false, appContext.getString(R.string.error_vflow_data_math_expression_empty))
        } else {
            ValidationResult(true)
        }
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val expressionPill = PillUtil.createPillFromParam(
            step.parameters[INPUT_EXPRESSION],
            getInputs().first { it.id == INPUT_EXPRESSION }
        )
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_data_math_expression_prefix),
            expressionPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val rawExpression = context.getVariableAsString(INPUT_EXPRESSION, "")
        val expression = VariableResolver.resolve(rawExpression, context).trim()

        if (expression.isEmpty()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_math_expression_input_error),
                appContext.getString(R.string.error_vflow_data_math_expression_empty)
            )
        }

        return try {
            val result = VNumber(Keval.eval(expression))
            assignResultIfRequested(context, result)
            ExecutionResult.Success(mapOf(OUTPUT_RESULT to result))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_math_expression_eval_error),
                e.localizedMessage ?: e.message ?: appContext.getString(R.string.error_vflow_data_math_expression_eval_failed)
            )
        }
    }

    private fun assignResultIfRequested(context: ExecutionContext, result: VNumber) {
        val variableRef = (context.getParameterRaw(INPUT_ASSIGN_TO)
            ?: context.getVariableAsString(INPUT_ASSIGN_TO, ""))
            .trim()
        if (variableRef.isEmpty()) return

        val namedVariablePath = VariablePathParser.parseNamedVariablePath(variableRef)
        val globalVariablePath = VariablePathParser.parseGlobalVariablePath(variableRef)
        val plainGlobalVariableName = variableRef
            .removePrefix("${VariablePathParser.GLOBAL_VARIABLE_NAMESPACE}.")
            .takeIf {
                variableRef.startsWith("${VariablePathParser.GLOBAL_VARIABLE_NAMESPACE}.") && it.isNotBlank()
            }

        val variableName = namedVariablePath?.firstOrNull()
            ?: globalVariablePath?.firstOrNull()
            ?: plainGlobalVariableName
            ?: return

        if (globalVariablePath != null || plainGlobalVariableName != null) {
            context.setGlobalVariable(variableName, result)
        } else {
            context.setVariable(variableName, result)
        }
    }
}
