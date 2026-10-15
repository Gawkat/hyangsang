package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.defaults.DefaultCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LookupReportRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val report = LookupReport(
        category = DefaultCategory.NORTH_KOREA,
        tapped = "경기",
        expected = "경기#2",
        gloss = "match",
        sentence = "거친 경기 끝에\t4강 좌절\n",
        shown = "경기#1",
        stems = listOf("경기"),
        tokens = "경기/Noun",
        note = "",
        articleUrl = "https://example.com/1"
    )

    @Test
    fun `rows start with the evaluation set columns`() {
        val columns = report.toTsvRow().split('\t')

        assertEquals(10, columns.size)
        assertEquals(listOf("North Korea", "경기", "경기#2", "match", "거친 경기 끝에 4강 좌절"), columns.take(5))
        assertEquals("", columns[8])
    }

    @Test
    fun `reports are counted, exported with a header and cleared`() = runBlocking {
        val repository = LookupReportRepository(File(folder.root, "reports.tsv"))
        assertNull(repository.export())

        repository.add(report)
        repository.add(report.copy(expected = null, gloss = null))

        assertEquals(2, repository.reportCount.first())
        val lines = repository.export()!!.lines()
        assertEquals(LookupReport.TSV_HEADER, lines[0])
        assertEquals(3, lines.size)

        repository.clear()
        assertEquals(0, repository.reportCount.first())
        assertNull(repository.export())
    }
}
