package com.chaomixian.vflow.ui.workflow_editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import io.github.rosemoe.sora.widget.CodeEditor

class InlineScriptEditorSheet : BottomSheetDialogFragment() {
    var onDone: ((String) -> Unit)? = null
    var onVariableRequested: (() -> Unit)? = null
    private var editor: CodeEditor? = null
    private var content: LinearLayout? = null
    private var keyboardToolbar: View? = null
    private var baseContentPaddingBottom: Int = 0

    companion object {
        private const val ARG_SCRIPT = "script"

        fun newInstance(initialScript: String = ""): InlineScriptEditorSheet {
            return InlineScriptEditorSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SCRIPT, initialScript)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.sheet_inline_script_editor, container, false)
        content = view.findViewById<LinearLayout>(R.id.inline_script_content).apply {
            baseContentPaddingBottom = paddingBottom
        }
        keyboardToolbar = view.findViewById(R.id.inline_script_keyboard_toolbar)
        editor = view.findViewById<CodeEditor>(R.id.inline_script_code_editor).apply {
            setText(arguments?.getString(ARG_SCRIPT).orEmpty())
            SoraJavaScriptEditorConfig.applyTo(this)
        }
        view.findViewById<MaterialButton>(R.id.button_inline_script_variable).setOnClickListener {
            onVariableRequested?.invoke()
        }
        view.findViewById<Button>(R.id.button_inline_script_done).setOnClickListener {
            onDone?.invoke(editor?.text?.toString().orEmpty())
            dismiss()
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            keyboardToolbar?.isVisible = isKeyboardVisible
            val toolbarSpace = if (isKeyboardVisible) {
                keyboardToolbar?.height?.takeIf { it > 0 } ?: (48 * resources.displayMetrics.density).toInt()
            } else {
                0
            }
            content?.setPadding(
                content?.paddingLeft ?: 0,
                content?.paddingTop ?: 0,
                content?.paddingRight ?: 0,
                baseContentPaddingBottom + toolbarSpace
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
        return view
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * 0.86f).toInt()
        }
        bottomSheet.requestLayout()
        BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            peekHeight = bottomSheet.layoutParams.height
        }
    }

    fun insertVariable(variableReference: String) {
        editor?.insertText(variableReference, variableReference.length)
    }

    override fun onDestroyView() {
        editor?.release()
        editor = null
        content = null
        keyboardToolbar = null
        super.onDestroyView()
    }
}
