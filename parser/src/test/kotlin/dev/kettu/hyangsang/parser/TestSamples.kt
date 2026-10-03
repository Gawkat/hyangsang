package dev.kettu.hyangsang.parser

import org.junit.Assume.assumeTrue

/**
 * Reads a saved article page from `src/test/resources`, with line endings normalized.
 *
 * The samples are copies of news articles and aren't committed, so the calling test is skipped
 * when [name] is missing instead of failing.
 */
fun readSample(name: String): String {
    val text = object {}.javaClass.classLoader.getResource(name)?.readText()
    assumeTrue("Sample $name not found in src/test/resources, skipping", text != null)
    return text!!.replace(Regex("\\r\\n?"), "\n")
}
