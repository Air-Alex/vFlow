package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorKeyboardToolbarTarget
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.DictionaryKVAdapter
import com.chaomixian.vflow.ui.workflow_editor.SoraJavaScriptEditorConfig
import io.github.rosemoe.sora.widget.CodeEditor

class JsEditorViewHolder(
    view: View,
    val scriptInput: CodeEditor,
    val inputsAdapter: DictionaryKVAdapter
) : CustomEditorViewHolder(view) {
    override fun getKeyboardToolbarTargets(): List<CustomEditorKeyboardToolbarTarget> {
        return listOf(CustomEditorKeyboardToolbarTarget("script", scriptInput))
    }

    override fun insertVariable(inputId: String, variableReference: String): Boolean {
        if (inputId != "script") return false
        scriptInput.insertText(variableReference, variableReference.length)
        return true
    }

    override fun onDestroy() {
        scriptInput.release()
    }
}

class JsModuleUIProvider : ModuleUIProvider {

    override fun getHandledInputIds(): Set<String> = setOf("script", "inputs")

    /**
     * 更新方法签名以匹配 ModuleUIProvider 接口。
     */
    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? = null

    /**
     * 更新方法签名以匹配 ModuleUIProvider 接口。
     */
    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((Intent, (Int, Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val inflater = LayoutInflater.from(context)
        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(padding, padding, padding, padding)
        }

        val scriptLabel = TextView(context).apply {
            text = context.getString(R.string.param_vflow_system_js_script_name)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
        }
        view.addView(scriptLabel)

        val scriptInput = CodeEditor(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (280 * context.resources.displayMetrics.density).toInt()
            ).apply {
                topMargin = (8 * context.resources.displayMetrics.density).toInt()
                bottomMargin = (16 * context.resources.displayMetrics.density).toInt()
            }
            setText(currentParameters["script"] as? String ?: "")
            SoraJavaScriptEditorConfig.applyTo(this)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
        view.addView(scriptInput)

        val inputsEditorView = inflater.inflate(R.layout.partial_dictionary_editor, view, false)
        val inputsRecyclerView = inputsEditorView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_view_dictionary)
        val addInputButton = inputsEditorView.findViewById<android.widget.Button>(R.id.button_add_kv_pair)
        addInputButton.setText(R.string.button_add_input)

        val currentInputs = (currentParameters["inputs"] as? Map<*, *>)
            ?.mapNotNull { (key, value) ->
                key?.toString()?.let { kStr ->
                    kStr to (value?.toString() ?: "")
                }
            }
            ?.toMutableList() ?: mutableListOf()

        val inputsAdapter = DictionaryKVAdapter(currentInputs, allSteps) { key ->
            if (key.isNotBlank()) {
                onMagicVariableRequested?.invoke("inputs.$key")
            }
        }

        inputsRecyclerView.adapter = inputsAdapter
        inputsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)

        addInputButton.setOnClickListener {
            inputsAdapter.addItem()
        }
        view.addView(inputsEditorView)

        return JsEditorViewHolder(view, scriptInput, inputsAdapter)
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val jsHolder = holder as JsEditorViewHolder
        val script = jsHolder.scriptInput.text.toString()
        val inputs = jsHolder.inputsAdapter.getItemsAsMap()
        return mapOf("script" to script, "inputs" to inputs)
    }
}
