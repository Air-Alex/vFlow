package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.os.Bundle
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.workflow.GlobalVariableStore
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch

class SoraJavaScriptCompletions(
    private val context: Context,
    private val delegate: TextMateLanguage
) : Language {
    private val apiRoot by lazy { VFlowJavaScriptApiCatalog.build(context.applicationContext) }

    override fun getAnalyzeManager(): AnalyzeManager = delegate.analyzeManager

    override fun getInterruptionLevel(): Int = delegate.interruptionLevel

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        delegate.requireAutoComplete(content, position, publisher, extraArguments)
        val request = JavaScriptCompletionRequest.from(content, position) ?: return
        val node = resolveNode(request.objectPath) ?: return
        val items = node.children.values
            .asSequence()
            .filter { it.name.startsWith(request.prefix, ignoreCase = true) }
            .sortedWith(compareBy<VFlowJavaScriptApiCatalog.Node> { it.kind.sortOrder }.thenBy { it.name })
            .map { it.toCompletionItem(request.prefix.length) }
            .toList()

        if (items.isNotEmpty()) {
            publisher.addItems(items)
        }
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        return delegate.getIndentAdvance(content, line, column)
    }

    override fun getIndentAdvance(
        content: ContentReference,
        line: Int,
        column: Int,
        spaceCountOnLine: Int,
        tabCountOnLine: Int
    ): Int {
        return delegate.getIndentAdvance(content, line, column, spaceCountOnLine, tabCountOnLine)
    }

    override fun useTab(): Boolean = delegate.useTab()

    override fun getFormatter(): Formatter = delegate.formatter

    override fun getSymbolPairs(): SymbolPairMatch = delegate.symbolPairs

    override fun getNewlineHandlers(): Array<NewlineHandler>? = delegate.newlineHandlers

    override fun destroy() {
        delegate.destroy()
    }

    private fun resolveNode(path: List<String>): VFlowJavaScriptApiCatalog.Node? {
        if (path.isEmpty()) return apiRoot
        var node = apiRoot
        path.forEach { part ->
            node = node.children[part] ?: return null
        }
        return node
    }

    private fun VFlowJavaScriptApiCatalog.Node.toCompletionItem(prefixLength: Int): SimpleCompletionItem {
        val apiKind = kind
        val commit = if (apiKind == VFlowJavaScriptApiCatalog.Kind.Function) commitText else name
        return SimpleCompletionItem(label, description, prefixLength, commit).apply {
            detail = this@toCompletionItem.detail
                .takeIf { it.isNotBlank() }
                ?.let { "  $it" }
                .orEmpty()
            sortText = apiKind.sortOrder.toString().padStart(2, '0') + name
            filterText = name
            kind(
                when (apiKind) {
                    VFlowJavaScriptApiCatalog.Kind.Root,
                    VFlowJavaScriptApiCatalog.Kind.Namespace -> CompletionItemKind.Module
                    VFlowJavaScriptApiCatalog.Kind.Function -> CompletionItemKind.Function
                    VFlowJavaScriptApiCatalog.Kind.Variable -> CompletionItemKind.Variable
                }
            )
        }
    }
}

private object VFlowJavaScriptApiCatalog {
    enum class Kind(val sortOrder: Int) {
        Root(0),
        Namespace(1),
        Function(2),
        Variable(3)
    }

    data class Node(
        val name: String,
        val kind: Kind,
        val label: String = name,
        val detail: String = "",
        val description: String = "",
        val commitText: String = "",
        val children: MutableMap<String, Node> = linkedMapOf()
    )

    fun build(context: Context): Node {
        return Node("root", Kind.Root).apply {
            children["vflow"] = buildVFlowTree(context)
            children["vars"] = Node(
                name = "vars",
                kind = Kind.Namespace,
                detail = "Named variables",
                description = "Workflow-scoped named variables"
            )
            children["sys"] = Node(
                name = "sys",
                kind = Kind.Namespace,
                detail = "Magic variables",
                description = "Runtime magic variables from triggers and loops"
            )
            children["global"] = buildGlobalVariableTree(context)
            children["inputs"] = Node(
                name = "inputs",
                kind = Kind.Namespace,
                detail = "Script inputs",
                description = "Inputs passed to the JavaScript module"
            )
        }
    }

    private fun buildVFlowTree(context: Context): Node {
        val root = Node(
            name = "vflow",
            kind = Kind.Namespace,
            detail = "vFlow modules",
            description = "Callable workflow modules"
        )
        ModuleRegistry.getAllModules()
            .filter { it.id.startsWith("vflow.") }
            .forEach { module ->
                val parts = module.id.split('.').drop(1)
                if (parts.isEmpty()) return@forEach
                var current = root
                parts.dropLast(1).forEach { namespace ->
                    current = current.children.getOrPut(namespace) {
                        Node(namespace, Kind.Namespace)
                    }
                }
                val functionName = parts.last()
                current.children[functionName] = Node(
                    name = functionName,
                    kind = Kind.Function,
                    label = functionName,
                    detail = buildFunctionDetail(module),
                    description = module.metadata.getLocalizedName(context),
                    commitText = buildFunctionCommitText(functionName, module)
                )
            }
        return root
    }

    private fun buildGlobalVariableTree(context: Context): Node {
        return Node(
            name = "global",
            kind = Kind.Namespace,
            detail = "Global variables",
            description = "Persisted global variables"
        ).apply {
            GlobalVariableStore.getAll(context).forEach { (name, value) ->
                children[name] = Node(
                    name = name,
                    kind = Kind.Variable,
                    detail = value.type.getLocalizedName(context),
                    description = "global.$name"
                )
            }
        }
    }

    private fun buildFunctionDetail(module: ActionModule): String {
        val inputNames = module.getInputs()
            .filterNot { it.isHidden }
            .take(4)
            .joinToString(", ") { it.id }
        return if (inputNames.isBlank()) "()" else "({ $inputNames })"
    }

    private fun buildFunctionCommitText(functionName: String, module: ActionModule): String {
        val inputsById = module.getInputs().associateBy { it.id }
        val requiredInputs = module.aiMetadata
            ?.requiredInputIds
            .orEmpty()
            .mapNotNull { inputsById[it] }
            .filterNot { it.isHidden }

        if (requiredInputs.isEmpty()) return "$functionName({  })"

        val properties = requiredInputs.joinToString(", ") { input ->
            "${input.id}: ${input.defaultJavaScriptLiteral()}"
        }
        return "$functionName({ $properties })"
    }

    private fun InputDefinition.defaultJavaScriptLiteral(): String {
        defaultValue?.let { value ->
            return when (value) {
                is Boolean -> value.toString()
                is Number -> value.toString()
                is String -> value.toJavaScriptStringLiteral()
                else -> "null"
            }
        }

        return when (staticType) {
            ParameterType.BOOLEAN -> "false"
            ParameterType.NUMBER -> "0"
            ParameterType.STRING -> "\"\""
            ParameterType.ENUM -> options.firstOrNull()?.toJavaScriptStringLiteral() ?: "\"\""
            else -> "null"
        }
    }

    private fun String.toJavaScriptStringLiteral(): String {
        return "\"" + flatMap { char ->
            when (char) {
                '\\' -> listOf('\\', '\\')
                '"' -> listOf('\\', '"')
                '\n' -> listOf('\\', 'n')
                '\r' -> listOf('\\', 'r')
                '\t' -> listOf('\\', 't')
                else -> listOf(char)
            }
        }.joinToString("") + "\""
    }
}

private data class JavaScriptCompletionRequest(
    val objectPath: List<String>,
    val prefix: String
) {
    companion object {
        fun from(content: ContentReference, position: CharPosition): JavaScriptCompletionRequest? {
            val linePrefix = content.getLine(position.line).take(position.column)
            val expression = EXPRESSION_REGEX.find(linePrefix)?.value ?: return null
            val parts = expression.split('.')
            if (parts.isEmpty()) return null
            val prefix = if (expression.endsWith('.')) "" else parts.last()
            val objectPath = if (parts.size == 1) {
                emptyList()
            } else {
                if (parts.first() !in SUPPORTED_ROOTS) return null
                if (expression.endsWith('.')) parts.filter { it.isNotEmpty() } else parts.dropLast(1)
            }
            return JavaScriptCompletionRequest(objectPath, prefix)
        }

        private val SUPPORTED_ROOTS = setOf("vflow", "vars", "sys", "global", "inputs")
        private val EXPRESSION_REGEX = Regex("""[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*\.?[A-Za-z_$]*$""")
    }
}
