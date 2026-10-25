package dev.kettu.hyangsang.parser.epub

import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
 * zip headers, which can lie. Entry names are never used for files, so they can't escape anywhere.
 *
 * An archive with two entries of the same name, which some EPUB tools write, is copied without
 * the later ones first: Android's ZipFile refuses such archives, and the JVM's reads either entry.
 * [rewrite] copies any archive, so tests can check the copy.
 */
internal class EpubArchive(
    file: File,
    private val limits: EpubLimits,
    rewrite: Boolean = false
) : AutoCloseable {
    private var copy: File? = null
    private val zip = open(file, rewrite)
    private val entries = zip.entries().asSequence().take(limits.maxEntries + 1).toList()

    private val byName = entries.associateBy { it.name }
    private var totalRead = 0L

    init {
        if (entries.size > limits.maxEntries) {
            close()
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

    override fun close() {
        zip.close()
        copy?.delete()
    }

    private fun open(file: File, rewrite: Boolean): ZipFile {
        if (!rewrite) {
            try {
                val zip = ZipFile(file)
                val names = zip.entries().asSequence().take(limits.maxEntries + 1).map { it.name }.toList()
                // Which of two entries of a name the JVM's ZipFile reads isn't defined
                if (names.size == names.toSet().size) return zip
                zip.close()
            } catch (_: ZipException) {
                // Perhaps only its duplicate entries, which Android refuses and the copy leaves out
            }
        }
        val target = File.createTempFile("epub", ".zip", file.absoluteFile.parentFile)
        copy = target
        try {
            copyWithoutDuplicates(file, target)
            return ZipFile(target)
        } catch (e: IOException) {
            target.delete()
            throw if (e is ZipException) EpubInvalidException("Not a zip file", e) else e
        }
    }

    private fun copyWithoutDuplicates(source: File, target: File) {
        ZipInputStream(source.inputStream().buffered()).use { input ->
            ZipOutputStream(target.outputStream().buffered()).use { output ->
                val names = HashSet<String>()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (names.size >= limits.maxEntries) throw EpubTooLargeException()
                    if (!names.add(entry.name)) continue
                    output.putNextEntry(ZipEntry(entry.name))
                    var size = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        size += n
                        total += n
                        if (size > limits.maxEntryBytes || total > limits.maxTotalBytes) {
                            throw EpubTooLargeException()
                        }
                        output.write(buffer, 0, n)
                    }
                    output.closeEntry()
                }
            }
        }
    }

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
