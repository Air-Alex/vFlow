package com.chaomixian.vflow.core.workflow.module.ui.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * UI 元素的类型枚举
 */
enum class UiElementType {
    TEXT,        // 纯文本展示
    INPUT,       // 输入框
    BUTTON,      // 按钮
    SWITCH,      // 开关
    LIST_PICKER  // 下拉列表选择（单选）
}

/**
 * UI 元素的数据定义，用于在 Intent 中传递
 *
 * 新增的 [options] 字段用于承载 LIST_PICKER 等组件的额外配置。
 * 字段位于构造器末尾并带有默认值，向后兼容旧 Parcel 序列化数据时，
 * 如果 parcel 中缺少该字段，会被解析为 null 并落回 emptyList()。
 */
@Parcelize
data class UiElement(
    val id: String,          // 对应的变量名 (key)
    val type: UiElementType, // 组件类型
    val label: String,       // 显示的标签或标题
    val defaultValue: String,// 默认值（LIST_PICKER 下为默认选中项；其他类型保持原语义）
    val placeholder: String, // 提示词
    val isRequired: Boolean,  // 是否必填
    val triggerEvent: Boolean = true,
    val options: List<String> = emptyList()  // LIST_PICKER 的候选项；其他类型忽略
) : Parcelable
