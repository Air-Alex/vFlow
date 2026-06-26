package com.chaomixian.vflow.ui.workflow_editor

import com.chaomixian.vflow.core.workflow.WorkflowNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowAiGeneratorTest {

    @Test
    fun `sanitize generated workflow replaces null lists and adds manual trigger`() {
        val sanitized = WorkflowAiGenerator.sanitizeGeneratedWorkflow(
            """
                {
                  "id": "workflow-1",
                  "name": "AI 工作流",
                  "triggers": null,
                  "steps": null
                }
            """.trimIndent()
        )

        assertNotNull(sanitized.triggers)
        assertNotNull(sanitized.steps)
        assertEquals(1, sanitized.triggers.size)
        assertEquals(WorkflowNormalizer.MANUAL_TRIGGER_MODULE_ID, sanitized.triggers.first().moduleId)
        assertTrue(sanitized.steps.isEmpty())
    }
}
