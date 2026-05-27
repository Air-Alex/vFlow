package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import org.eclipse.tm4e.core.registry.IThemeSource
import java.nio.charset.StandardCharsets

object SoraJavaScriptEditorConfig {
    private const val SCOPE_JAVASCRIPT = "source.js"
    private const val LANGUAGES_PATH = "textmate/languages.json"
    private const val THEME_DARK_NAME = "vFlow JavaScript Dark"
    private const val THEME_DARK_PATH = "textmate/themes/vflow-javascript-theme.json"
    private const val THEME_LIGHT_NAME = "vFlow JavaScript Light"
    private const val THEME_LIGHT_PATH = "textmate/themes/vflow-javascript-light-theme.json"

    @Volatile
    private var grammarInitialized = false

    fun applyTo(editor: CodeEditor) {
        editor.setTextSize(14f)
        editor.setLineInfoTextSize(12f)
        editor.setTypefaceText(Typeface.MONOSPACE)
        editor.setTypefaceLineNumber(Typeface.MONOSPACE)
        editor.setTabWidth(2)
        editor.setWordwrap(false)
        editor.setPinLineNumber(true)
        editor.setHighlightCurrentLine(true)
        editor.setHighlightCurrentBlock(true)
        editor.setHighlightBracketPair(true)
        editor.setBlockLineEnabled(true)
        editor.setNonPrintablePaintingFlags(
            CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
                CodeEditor.FLAG_DRAW_TAB_SAME_AS_SPACE
        )
        editor.props.autoIndent = true
        editor.props.symbolPairAutoCompletion = true
        editor.props.disallowSuggestions = true
        editor.getComponent(EditorAutoCompletion::class.java).setEnabled(true)

        val language = createLanguage(editor.context)
        if (language !is EmptyLanguage) {
            applyTheme(editor.context)
            editor.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance()))
        }
        editor.setEditorLanguage(language)
        editor.rerunAnalysis()
    }

    private fun createLanguage(context: Context): Language {
        return runCatching {
            ensureGrammarLoaded(context.applicationContext)
            TextMateLanguage.create(SCOPE_JAVASCRIPT, true).apply {
                setTabSize(2)
                useTab(false)
                setCompleterKeywords(JAVASCRIPT_KEYWORDS)
            }.let { SoraJavaScriptCompletions(context.applicationContext, it) }
        }.getOrElse {
            EmptyLanguage()
        }
    }

    @Synchronized
    private fun ensureGrammarLoaded(context: Context) {
        if (grammarInitialized) return

        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.assets))
        GrammarRegistry.getInstance().loadGrammars(LANGUAGES_PATH)
        grammarInitialized = true
    }

    private fun applyTheme(context: Context) {
        val themeName = if (isNightMode(context)) THEME_DARK_NAME else THEME_LIGHT_NAME
        val themePath = if (isNightMode(context)) THEME_DARK_PATH else THEME_LIGHT_PATH
        val themeRegistry = ThemeRegistry.getInstance()
        if (themeRegistry.setTheme(themeName)) return

        val themeStream = context.assets.open(themePath)
        val themeModel = ThemeModel(
            IThemeSource.fromInputStream(themeStream, themePath, StandardCharsets.UTF_8),
            themeName
        ).apply {
            setDark(isNightMode(context))
        }

        themeRegistry.loadTheme(themeModel)
    }

    private fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private val JAVASCRIPT_KEYWORDS = arrayOf(
        "Array",
        "Boolean",
        "Date",
        "Error",
        "JSON",
        "Map",
        "Math",
        "Number",
        "Object",
        "Promise",
        "RegExp",
        "Set",
        "String",
        "Symbol",
        "WeakMap",
        "WeakSet",
        "async",
        "await",
        "break",
        "case",
        "catch",
        "class",
        "const",
        "continue",
        "debugger",
        "default",
        "delete",
        "do",
        "else",
        "export",
        "extends",
        "false",
        "finally",
        "for",
        "function",
        "if",
        "import",
        "in",
        "instanceof",
        "let",
        "new",
        "null",
        "return",
        "static",
        "super",
        "switch",
        "this",
        "throw",
        "true",
        "try",
        "typeof",
        "undefined",
        "var",
        "void",
        "while",
        "with",
        "yield"
    )
}
