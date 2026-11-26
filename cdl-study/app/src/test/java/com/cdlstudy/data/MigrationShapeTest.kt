package com.cdlstudy.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the migration SQL against the schema Room exports.
 *
 * This is not a migration test. A real one opens a version 2 database, runs
 * the migration and asks Room to validate the result, and that needs a device
 * or an emulator, neither of which exists in this build environment.
 *
 * What it does catch is the most likely mistake by a long way: a column named
 * one thing in the entity and another in the ALTER TABLE, which would brick
 * the app on first launch for anyone with existing data. A typo here is worth
 * more than a perfect test that never runs.
 */
class MigrationShapeTest {

    private val schemas = File("schemas/com.cdlstudy.data.AppDatabase")
    private val source = File("src/main/java/com/cdlstudy/data/AppDatabase.kt").readText()

    private fun columns(version: Int, table: String): List<String> {
        val json = File(schemas, "$version.json").readText()
        // The exported schema is one line per field; pulling the names out with
        // a regex avoids dragging a JSON parser into the test source set.
        val entity = json.substringAfter("\"tableName\": \"$table\"")
            .substringBefore("\"tableName\":")
        return Regex("\"fieldPath\": \"(\\w+)\"").findAll(entity).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `every column version 3 added to cards is in the migration`() {
        val before = columns(2, "cards").toSet()
        val after = columns(3, "cards")
        val added = after.filterNot { it in before }
        assertTrue("version 3 added nothing to cards", added.isNotEmpty())
        added.forEach { name ->
            assertTrue(
                "the migration never adds the column '$name', so it would fail validation",
                source.contains("ADD COLUMN $name "),
            )
        }
    }

    @Test
    fun `every column version 2 added to exam attempts is in the migration`() {
        val before = columns(1, "exam_attempts").toSet()
        val after = columns(2, "exam_attempts")
        val added = after.filterNot { it in before }
        assertTrue("version 2 added nothing to exam_attempts", added.isNotEmpty())
        added.forEach { name ->
            assertTrue(
                "the migration never adds the column '$name'",
                source.contains("ADD COLUMN $name "),
            )
        }
    }

    @Test
    fun `the migration adds nothing the schema does not expect`() {
        val expected = (columns(3, "cards") + columns(3, "exam_attempts")).toSet()
        Regex("ADD COLUMN (\\w+) ").findAll(source).forEach { match ->
            val name = match.groupValues[1]
            assertTrue("the migration adds '$name', which no entity declares", name in expected)
        }
    }

    @Test
    fun `every version between the first and the current has a migration`() {
        val versions = schemas.listFiles { f: File -> f.name.endsWith(".json") }!!
            .map { it.name.removeSuffix(".json").toInt() }
            .sorted()
        versions.zipWithNext().forEach { (from, to) ->
            assertTrue(
                "nothing migrates $from to $to, so the app would crash on upgrade",
                source.contains("Migration($from, $to)"),
            )
        }
    }

    @Test
    fun `there is no destructive fallback, which would wipe a study record`() {
        listOf("fallbackToDestructiveMigration", "fallbackToDestructiveMigrationOnDowngrade")
            .forEach { call ->
                assertTrue(
                    "$call would silently delete everything the user has done",
                    !source.contains(call),
                )
            }
    }
}
