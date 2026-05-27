// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/RichTextView.kt
// 描述: 支持变量药丸的编辑器（重构后 - 职责简化，仅负责编辑逻辑）
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.Spanned
import android.util.AttributeSet
import android.view.MotionEvent
import com.google.android.material.textfield.TextInputEditText
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * 支持变量药丸的编辑器
 *
 * 职责（重构后）：
 * - 仅处理编辑逻辑（光标管理、文本输入）
 * - 渲染委托给 PillRenderer
 *
 * 不再负责：
 * - 变量解析（由 PillVariableResolver 处理）
 * - Pill 视觉渲染（由 PillRenderer 处理）
 */
class RichTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr) {

    private val pillHitInsetX: Float = 12f * resources.displayMetrics.density

    // 保存 allSteps 引用，用于 insertVariable 方法
    private var currentAllSteps: List<ActionStep> = emptyList()
    var onInlineScriptEditRequested: ((currentCode: String, onUpdated: (String) -> Unit) -> Unit)? = null
    var onVariablePillEditRequested: ((currentReference: String, onUpdated: (String?) -> Unit) -> Unit)? = null

    /**
     * 设置富文本内容
     *
     * @param rawText 包含变量引用的原始文本
     * @param allSteps 工作流中的所有步骤，用于解析变量
     */
    fun setRichText(rawText: String, allSteps: List<ActionStep> = emptyList()) {
        currentAllSteps = allSteps
        // 使用 PillRenderer 的统一渲染方法，EDIT 模式用于编辑器
        val spannable = PillRenderer.renderToSpannable(
            rawText,
            PillRenderer.RenderMode.EDIT,
            allSteps,
            context
        )
        setText(spannable)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val editable = text
            if (editable is Spanned) {
                val pillSpan = findPillSpanAt(event.x, event.y, editable)
                val inlineScriptCode = pillSpan?.inlineScriptCode
                if (pillSpan != null && inlineScriptCode != null) {
                    val start = editable.getSpanStart(pillSpan)
                    val end = editable.getSpanEnd(pillSpan)
                    onInlineScriptEditRequested?.invoke(inlineScriptCode) { updatedCode ->
                        replaceInlineScriptPill(start, end, updatedCode)
                    }
                    return true
                }
                val variableReference = pillSpan?.variableReference
                if (pillSpan != null && variableReference != null) {
                    val start = editable.getSpanStart(pillSpan)
                    val end = editable.getSpanEnd(pillSpan)
                    onVariablePillEditRequested?.invoke(variableReference) { updatedReference ->
                        if (updatedReference == null) {
                            removePill(start, end)
                        } else {
                            replaceVariablePill(start, end, updatedReference)
                        }
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findPillSpanAt(x: Float, y: Float, editable: Spanned): PillRenderer.RoundedBackgroundSpan? {
        val layout = layout ?: return null
        val hitX = x - totalPaddingLeft + scrollX
        val hitY = y - totalPaddingTop + scrollY
        val line = layout.getLineForVertical(hitY.toInt())
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        val textPaint = TextPaint(paint)
        var cursorX = layout.getLineLeft(line)
        var cursorOffset = lineStart

        editable.getSpans(lineStart, lineEnd, PillRenderer.RoundedBackgroundSpan::class.java)
            .sortedBy { editable.getSpanStart(it) }
            .forEach { span ->
            if (span.variableReference == null && span.inlineScriptCode == null) {
                return@forEach
            }
            val start = editable.getSpanStart(span)
            val end = editable.getSpanEnd(span)
            if (start < lineStart || end <= start || end > lineEnd) {
                return@forEach
            }

            if (cursorOffset < start) {
                cursorX += textPaint.measureText(
                    editable,
                    cursorOffset,
                    start
                )
            }

            val spanWidth = span.getSize(textPaint, editable, start, end, null).toFloat()
            val bounds = span.getClickableBounds(
                paint = textPaint,
                text = editable,
                start = start,
                end = end,
                x = cursorX,
                baseline = layout.getLineBaseline(line),
                horizontalInset = pillHitInsetX
            )

            if (bounds.contains(hitX, hitY)) {
                return span
            }
            cursorX += spanWidth
            cursorOffset = end
        }
        return null
    }

    /**
     * 获取纯文本（原始变量引用）
     */
    fun getRawText(): String {
        val editable = text ?: return ""
        val rawText = SpannableStringBuilder(editable)
        rawText.getSpans(0, rawText.length, PillRenderer.RoundedBackgroundSpan::class.java)
            .filter { it.inlineScriptCode != null && it.rawText != null }
            .sortedByDescending { rawText.getSpanStart(it) }
            .forEach { span ->
                val start = rawText.getSpanStart(span)
                val end = rawText.getSpanEnd(span)
                if (start >= 0 && end >= start) {
                    rawText.replace(start, end, span.rawText.orEmpty())
                }
            }
        return rawText.toString()
    }

    /**
     * 在当前光标位置插入一个变量"药丸"
     *
     * @param variableReference 变量引用（如 "{{step1.output}}"）
     */
    fun insertVariablePill(variableReference: String) {
        // 使用 PillRenderer 渲染单个 Pill，EDIT 模式用于编辑器
        val pillSpannable = PillRenderer.renderSinglePill(
            variableReference,
            PillRenderer.RenderMode.EDIT,
            currentAllSteps,
            context
        )

        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        val spannable = text ?: return

        // 插入到文本中
        spannable.replace(start, end, pillSpannable)

        // 将光标移动到插入内容的末尾
        setSelection(start + pillSpannable.length)
    }

    fun insertInlineScriptPill(script: String) {
        val pillSpannable = PillRenderer.renderSingleInlineScriptPill(
            script,
            currentAllSteps,
            context
        )

        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        val spannable = text ?: return
        spannable.replace(start, end, pillSpannable)
        setSelection(start + pillSpannable.length)
    }

    private fun replaceInlineScriptPill(start: Int, end: Int, script: String) {
        val pillSpannable = PillRenderer.renderSingleInlineScriptPill(
            script,
            currentAllSteps,
            context
        )
        val spannable = text ?: return
        spannable.replace(start, end, pillSpannable)
        setSelection(start + pillSpannable.length)
    }

    private fun replaceVariablePill(start: Int, end: Int, variableReference: String) {
        val pillSpannable = PillRenderer.renderSinglePill(
            variableReference,
            PillRenderer.RenderMode.EDIT,
            currentAllSteps,
            context
        )
        val spannable = text ?: return
        spannable.replace(start, end, pillSpannable)
        setSelection(start + pillSpannable.length)
    }

    private fun removePill(start: Int, end: Int) {
        val spannable = text ?: return
        spannable.delete(start, end)
        setSelection(start.coerceAtMost(spannable.length))
    }
}
