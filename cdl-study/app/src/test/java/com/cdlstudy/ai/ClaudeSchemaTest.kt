package com.cdlstudy.ai

import com.cdlstudy.domain.AiGrading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schema is written as text in [AiGrading.schema] and handed to the SDK as
 * a map of JSON values. This checks the hand-off, which no other test covers:
 * if the nesting were lost, the request would go out with an empty schema and
 * the reply would come back as prose.
 */
class ClaudeSchemaTest {

    private val schema = Claude.schemaOf(AiGrading.schema())._additionalProperties()

    @Test
    fun `the top level keys survive the hand-off`() {
        assertEquals(setOf("type", "additionalProperties", "required", "properties"), schema.keys)
        assertEquals("object", schema["type"]!!.convert(String::class.java))
    }

    @Test
    fun `the nested properties survive, not just the top level`() {
        val text = schema["properties"].toString()
        listOf("marks", "factId", "HIT", "PARTIAL", "MISS", "errors", "note").forEach {
            assertTrue("the schema lost $it on the way to the SDK", text.contains(it))
        }
    }

    @Test
    fun `every field the parser reads is required`() {
        val required = schema["required"].toString()
        listOf("marks", "errors", "note").forEach { assertTrue(required.contains(it)) }
    }
}
