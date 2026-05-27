package com.chaomixian.vflow.core.types.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateParserTest {

    @Test
    fun `parse splits text variables and inline scripts`() {
        val segments = TemplateParser("a {{step.output}} {%return 1%} b").parse()

        assertEquals(5, segments.size)
        assertEquals(TemplateSegment.Text("a "), segments[0])
        assertEquals("{{step.output}}", (segments[1] as TemplateSegment.Variable).rawExpression)
        assertEquals(TemplateSegment.Text(" "), segments[2])
        assertEquals(TemplateSegment.Script("return 1", "{%return 1%}"), segments[3])
        assertEquals(TemplateSegment.Text(" b"), segments[4])
    }

    @Test
    fun `parse treats escaped inline script delimiters as text`() {
        val segments = TemplateParser("\\{% text \\%}").parse()

        assertEquals(listOf(TemplateSegment.Text("{% text %}")), segments)
        assertFalse(segments.any { it is TemplateSegment.Script })
    }

    @Test
    fun `parse ignores incomplete inline script template`() {
        val input = "before {%return 1"
        val segments = TemplateParser(input).parse()

        assertEquals(listOf(TemplateSegment.Text(input)), segments)
        assertFalse(segments.any { it is TemplateSegment.Script })
    }

    @Test
    fun `inline script parser preserves escaped close delimiter inside code`() {
        val segments = TemplateParser("{%return \"\\%}\"%}").parse()

        assertEquals(listOf(TemplateSegment.Script("return \"%}\"", "{%return \"\\%}\"%}")), segments)
        assertTrue(segments.single() is TemplateSegment.Script)
    }

    @Test
    fun `parse keeps multiline inline script as one segment`() {
        val raw = "{%{{4ed1b0d4-36ef-426c-93a6-66d8faf63865.result}}\n\n{{5983aea8-1397-4741-bb8c-90ceab64124b.success}}%}"
        val segments = TemplateParser(raw).parse()

        assertEquals(
            listOf(
                TemplateSegment.Script(
                    "{{4ed1b0d4-36ef-426c-93a6-66d8faf63865.result}}\n\n{{5983aea8-1397-4741-bb8c-90ceab64124b.success}}",
                    raw
                )
            ),
            segments
        )
    }
}
