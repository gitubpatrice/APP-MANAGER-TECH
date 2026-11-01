package com.filestech.appmanager.data.system

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Shape of the bundled `trackers.json`, checked at build time. [TrackerDatabase] falls back to an empty
 * list on a malformed asset, which would show every app as clean: this test is what keeps that from
 * shipping. It reads the file line by line (one tracker per line), as `org.json` is a stub in JVM tests.
 */
class TrackerAssetTest {

    private val lines = File("src/main/assets/trackers.json").readLines()
        .map { it.trim() }
        .filter { it.startsWith("{") }

    private data class Entry(val id: String, val name: String, val category: String, val signatures: List<String>)

    private val entries = lines.map { line ->
        fun field(key: String) = Regex("\"$key\":\"([^\"]+)\"").find(line)?.groupValues?.get(1)
        val sigs = Regex("\"signatures\":\\[([^]]*)]").find(line)?.groupValues?.get(1).orEmpty()
        Entry(
            id         = field("id") ?: error("no id: $line"),
            name       = field("name") ?: error("no name: $line"),
            category   = field("category") ?: error("no category: $line"),
            signatures = Regex("\"([^\"]+)\"").findAll(sigs).map { it.groupValues[1] }.toList(),
        )
    }

    @Test
    fun `the asset holds 43 distinct trackers, the count the website states`() {
        assertThat(entries).hasSize(43)
        assertThat(entries.map { it.id }).containsNoDuplicates()
        assertThat(entries.map { it.name }).containsNoDuplicates()
    }

    @Test
    fun `every tracker has a category the app translates, and at least one signature`() {
        val translated = setOf(
            "Ads", "Analytics", "Attribution", "Crash reporter", "Identification", "Profiling", "Push", "Session replay",
        )
        entries.forEach { e ->
            assertThat(translated).contains(e.category)
            assertThat(e.signatures).isNotEmpty()
        }
    }

    @Test
    fun `a signature is a package prefix ending in a dot, or one class`() {
        entries.flatMap { it.signatures }.forEach { sig ->
            val isPackage = sig.endsWith('.')
            val isClass = sig.substringAfterLast('.').firstOrNull()?.isUpperCase() == true
            assertThat(isPackage || isClass).isTrue()
        }
    }

    @Test
    fun `no signature repeats, nor starts with another tracker's signature`() {
        val all = entries.flatMap { e -> e.signatures.map { it to e.id } }
        assertThat(all.map { it.first }).containsNoDuplicates()
        // matchTrackers stops at the first matching signature: an overlap would make the order decide.
        for ((sig, id) in all) {
            for ((other, otherId) in all) {
                if (id != otherId) assertThat(sig.startsWith(other)).isFalse()
            }
        }
    }
}
