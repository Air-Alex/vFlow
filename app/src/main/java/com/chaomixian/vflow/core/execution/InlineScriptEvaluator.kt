package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.types.parser.TemplateParser
import com.chaomixian.vflow.core.types.parser.TemplateSegment
import com.google.gson.Gson

object InlineScriptEvaluator {
    private val gson = Gson()

    class InlineScriptExecutionException(
        val code: String,
        cause: Throwable
    ) : RuntimeException(buildMessage(code, cause), cause)

    fun hasInlineScript(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        return TemplateParser(text).parse().any { it is TemplateSegment.Script }
    }

    fun resolve(text: String, context: ExecutionContext): String {
        if (!hasInlineScript(text)) return text
        val builder = StringBuilder()
        TemplateParser(text).parse().forEach { segment ->
            when (segment) {
                is TemplateSegment.Text -> builder.append(segment.content)
                is TemplateSegment.Variable -> builder.append(segment.rawExpression)
                is TemplateSegment.Script -> {
                    builder.append(evaluateScript(segment.code, context))
                }
            }
        }
        return builder.toString()
    }

    private fun evaluateScript(code: String, context: ExecutionContext): String {
        return try {
            val resolvedScript = interpolateVariablesAsJavaScript(code, context)
            val result = JsExecutor(context).execute(resolvedScript, mutableMapOf())
            val value = result["result"] ?: result.takeIf { it.isNotEmpty() }
            value?.toString().orEmpty()
        } catch (e: InlineScriptExecutionException) {
            throw e
        } catch (e: Exception) {
            throw InlineScriptExecutionException(code, e)
        }
    }

    private fun interpolateVariablesAsJavaScript(code: String, context: ExecutionContext): String {
        val builder = StringBuilder()
        TemplateParser(code).parse().forEach { segment ->
            when (segment) {
                is TemplateSegment.Text -> builder.append(segment.content)
                is TemplateSegment.Variable -> {
                    val value = VariableResolver.resolveValue(segment.rawExpression, context)
                    builder.append(gson.toJson(value))
                }
                is TemplateSegment.Script -> builder.append(segment.rawExpression)
            }
        }
        return builder.toString()
    }

    private fun buildMessage(code: String, cause: Throwable): String {
        val message = cause.localizedMessage ?: cause.message ?: "未知错误"
        val firstLine = code.trim().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return if (firstLine.isBlank()) {
            "Inline JavaScript 执行失败: $message"
        } else {
            "Inline JavaScript 执行失败: $message\n脚本: $firstLine"
        }
    }
}
