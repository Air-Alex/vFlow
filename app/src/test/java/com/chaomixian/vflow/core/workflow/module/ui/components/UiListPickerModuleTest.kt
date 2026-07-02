package com.chaomixian.vflow.core.workflow.module.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UiListPickerModule 的核心逻辑单元测试。
 *
 * 主要验证：
 * - 选项解析行为（换行分割、空行忽略、首尾空白裁剪）。
 * - 输入定义的稳定性（id 与字段顺序）。
 * - 模块元数据兜底文案。
 */
class UiListPickerModuleTest {

    private val module = UiListPickerModule()

    @Test
    fun `module id is the documented vflow ui component listpicker id`() {
        assertEquals("vflow.ui.component.listpicker", module.id)
    }

    @Test
    fun `module category is UI components`() {
        assertEquals("UI 组件", module.metadata.category)
        assertEquals("ui", module.metadata.categoryId)
    }

    @Test
    fun `module exposes five inputs in the expected order`() {
        val ids = module.getInputs().map { it.id }
        assertEquals(
            listOf("key", "label", "options", "default_value", "trigger_event"),
            ids
        )
    }

    @Test
    fun `parseOptions splits on newline and trims whitespace`() {
        val parsed = UiListPickerModule.parseOptions("  Apple \n Banana\nCherry  ")
        assertEquals(listOf("Apple", "Banana", "Cherry"), parsed)
    }

    @Test
    fun `parseOptions ignores empty lines`() {
        val parsed = UiListPickerModule.parseOptions("Apple\n\n\nBanana\n   \nCherry\n")
        assertEquals(listOf("Apple", "Banana", "Cherry"), parsed)
    }

    @Test
    fun `parseOptions returns empty list for blank input`() {
        assertEquals(emptyList<String>(), UiListPickerModule.parseOptions(""))
        assertEquals(emptyList<String>(), UiListPickerModule.parseOptions("   \n\n  "))
    }

    @Test
    fun `parseOptions treats backslash-n as a newline marker`() {
        // 支持常见被 JSON 转义后的 \\n
        val parsed = UiListPickerModule.parseOptions("Apple\\nBanana\\nCherry")
        assertEquals(listOf("Apple\nBanana\nCherry"), parsed)
    }

    @Test
    fun `parseOptions preserves internal spaces`() {
        val parsed = UiListPickerModule.parseOptions("Hello World\nFoo Bar")
        assertEquals(listOf("Hello World", "Foo Bar"), parsed)
    }

    @Test
    fun `metadata name fallback is the localized Chinese label`() {
        // 当 R.string 资源不可用时，模块应仍能 fallback 到写死的字符串
        assertEquals("列表选择", module.metadata.name)
        assertTrue(module.metadata.description.isNotEmpty())
    }
}
