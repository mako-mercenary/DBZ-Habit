package com.cdlstudy.domain

import java.io.File

/**
 * The real content files, for a test that must run against what ships.
 *
 * A test that builds its own two-fact section proves the rule. A test that
 * reads these proves the content obeys it.
 */
object RealContent {

    private val folder = File("src/main/assets/content")

    fun files(): List<File> =
        folder.listFiles { f: File -> f.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()

    fun load(): Content = ContentReader.readAll(files().map { it.readText() })
}
