package dev.kettu.hyangsang.parser.epub

import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** How much of an EPUB the parser reads before giving up, against zip bombs. */
data class EpubLimits(
    val maxEntries: Int = 10_000,
    // Per entry, and for everything read from the archive together, uncompressed
    val maxEntryBytes: Long = 50L * 1024 * 1024,
    val maxTotalBytes: Long = 300L * 1024 * 1024
)

internal class EpubTooLargeException : IOException("EPUB exceeds the size limits")

/**
 * Read access to the files of an EPUB. Sizes are counted while reading rather than taken from the
 * zip headers, which can lie. Nothing here writes files, so entry names can't escape anywhere.
 */
internal class EpubArchive(file: File, private val limits: EpubLimits) : AutoCloseable {
    private val zip = try {
        ZipFile(file)
    } catch (e: ZipException) {
        throw EpubInvalidException("Not a zip file", e)
    }
    private val entries = zip.entries().asSequence().take(limits.maxEntries + 1).toList()
    private val byName = entries.associateBy { it.name }
    private var totalRead = 0L

    init {
        if (entries.size > limits.maxEntries) {
            zip.close()
            throw EpubTooLargeException()
        }
    }

    fun contains(path: String): Boolean = byName[path]?.isDirectory == false

    /** The bytes of the file at [path], or null if there's no such file. */
    fun read(path: String): ByteArray? {
        val entry = byName[path]?.takeUnless { it.isDirectory } ?: return null
        zip.getInputStream(entry).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var size = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                size += n
                totalRead += n
                if (size > limits.maxEntryBytes || totalRead > limits.maxTotalBytes) {
                    throw EpubTooLargeException()
                }
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }

    fun readText(path: String): String? = read(path)?.toString(Charsets.UTF_8)

    override fun close() = zip.close()

    companion object {
        /**
         * Resolves [href] from a file in the folder [base] (empty for the root) to a path in the
         * archive, without its #fragment. Returns null for paths that leave the archive's root,
         * absolute paths and links to other sites.
         */
        fun resolve(base: String, href: String): String? {
            val path = href.substringBefore('#')
            if (path.isEmpty() || path.startsWith("/") || ':' in path.substringBefore('/')) {
                return null
            }
            val decoded = try {
                URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
            } catch (_: IllegalArgumentException) {
                path
            }
            val segments = ArrayDeque<String>()
            base.split('/').filter { it.isNotEmpty() }.forEach { segments.addLast(it) }
            for (segment in decoded.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (segments.isEmpty()) return null else segments.removeLast()
                    else -> segments.addLast(segment)
                }
            }
            return segments.joinToString("/").ifEmpty { null }
        }

        fun folderOf(path: String): String = path.substringBeforeLast('/', "")
    }
}

internal class EpubInvalidException(message: String, cause: Throwable? = null) : IOException(message, cause)
