package dev.kettu.hyangsang.parser.epub

import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.parser.ContentSpan
import dev.kettu.hyangsang.parser.SpanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EpubParserTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val imageDir by lazy { File(folder.root, "images") }

    private fun parse(epub: TestEpub, limits: EpubLimits = EpubLimits()): EpubResult =
        EpubParser(limits).parse(epub.writeTo(folder.newFile("book.epub")), imageDir)

    private fun book(epub: TestEpub): EpubBook {
        val result = parse(epub)
        assertTrue("Expected a book, got $result", result is EpubResult.Success)
        return (result as EpubResult.Success).book
    }

    private fun failure(epub: TestEpub): EpubFailure? = (parse(epub) as? EpubResult.Failure)?.reason

    private fun texts(chapter: EpubChapter): List<String> =
        chapter.blocks.filterIsInstance<ContentBlock.Text>().map { it.text }

    @Test
    fun readsMetadataAndChaptersFromTheNavDocument() {
        val book = book(
            TestEpub()
                .nav("one.xhtml" to "첫째 장", "two.xhtml" to "둘째 장")
                .chapter("one.xhtml", "<h1>첫째 장</h1><p>첫 문단.</p>")
                .chapter("two.xhtml", "<p>둘째 문단.</p>")
        )
        assertEquals("테스트 책", book.title)
        assertEquals("김작가", book.author)
        assertEquals("ko", book.language)
        assertEquals(listOf("첫째 장", "둘째 장"), book.chapters.map { it.title })
        // The heading that repeats the chapter's title is left to the reader's header
        assertEquals(listOf(ContentBlock.Text("첫 문단.")), book.chapters[0].blocks)
        assertEquals(listOf("둘째 문단."), texts(book.chapters[1]))
    }

    @Test
    fun keepsSectionBreaksAsDividers() {
        val book = book(
            TestEpub()
                .nav("one.xhtml" to "첫째 장")
                .chapter("one.xhtml", "<p>앞 문단.</p><hr/><p>뒤 문단.</p>")
        )
        assertEquals(
            listOf(ContentBlock.Text("앞 문단."), ContentBlock.Divider, ContentBlock.Text("뒤 문단.")),
            book.chapters.single().blocks
        )
    }

    @Test
    fun dropsADividerPageThatRepeatsTheTitle() {
        val book = book(
            TestEpub()
                .nav("part1.xhtml" to "1부 ─ 바다", "part2.xhtml" to "나의 오후")
                .chapter("part1.xhtml", "<h1>1</h1><p>바다</p>")
                .chapter("text1.xhtml", "<p>바다는 조용했다.</p>")
                .chapter("part2.xhtml", "<p>나의</p><p>오후</p><p>나의</p><p>오후가 길었다.</p>")
        )
        assertEquals(listOf("바다는 조용했다."), texts(book.chapters[0]))
        // Lines that only happen to be in the title add up to more than it
        assertEquals(listOf("나의", "오후", "나의", "오후가 길었다."), texts(book.chapters[1]))
    }

    @Test
    fun readsChaptersFromAnNcx() {
        val book = book(
            TestEpub()
                .ncx("one.xhtml" to "하나", "two.xhtml" to "둘")
                .chapter("one.xhtml", "<p>가</p>")
                .chapter("two.xhtml", "<p>나</p>")
        )
        assertEquals(listOf("하나", "둘"), book.chapters.map { it.title })
    }

    @Test
    fun startsChaptersAtFragmentsWithinADocument() {
        val book = book(
            TestEpub()
                .nav("all.xhtml#a" to "가 장", "all.xhtml#b" to "나 장")
                .chapter("all.xhtml", """<div><h2 id="a">가 장</h2><p>가 문단.</p><h2 id="b">나 장</h2><p>나 문단.</p></div>""")
        )
        assertEquals(listOf("가 장", "나 장"), book.chapters.map { it.title })
        assertEquals(listOf("가 문단."), texts(book.chapters[0]))
        assertEquals(listOf("나 문단."), texts(book.chapters[1]))
    }

    @Test
    fun continuesAChapterInDocumentsTheContentsSkip() {
        val book = book(
            TestEpub()
                .nav("one.xhtml" to "첫째 장")
                .chapter("one.xhtml", "<p>앞부분.</p>")
                .chapter("one-2.xhtml", "<p>뒷부분.</p>")
        )
        assertEquals(1, book.chapters.size)
        assertEquals(listOf("앞부분.", "뒷부분."), texts(book.chapters[0]))
    }

    @Test
    fun makesEachDocumentAChapterWithoutContents() {
        val book = book(
            TestEpub()
                .chapter("one.xhtml", "<h1>제목 하나</h1><p>가</p>")
                .chapter("two.xhtml", "<p>나</p>")
                .chapter("skipped.xhtml", "<p>부록</p>", itemref = """linear="no"""")
        )
        assertEquals(listOf("제목 하나", null), book.chapters.map { it.title })
        assertEquals(listOf("가"), texts(book.chapters[0]))
    }

    @Test
    fun dropsALeadingCoverPageAndKeepsLaterImagesWithTheirChapter() {
        val book = book(
            TestEpub()
                .file("OEBPS/pic.png", TestEpub.PNG)
                .chapter("cover.xhtml", """<img src="pic.png" alt="cover"/>""")
                .chapter("one.xhtml", "<p>본문.</p>")
                .chapter("plate.xhtml", """<div><img src="pic.png"/></div>""")
        )
        assertEquals(1, book.chapters.size)
        assertEquals(listOf("본문."), texts(book.chapters[0]))
        assertTrue(book.chapters[0].blocks.last() is ContentBlock.Image)
    }

    @Test
    fun makesParagraphsOfBareTextAndKeepsLineBreaks() {
        val chapter = book(
            TestEpub().chapter(
                "one.xhtml",
                """<div>바깥 글.<div>안쪽 글 <b>굵게</b>.</div>꼬리 글.</div><p class="poem">첫 줄<br/>둘째 줄</p>"""
            )
        ).chapters.single()
        assertEquals(listOf("바깥 글.", "안쪽 글 굵게.", "꼬리 글.", "첫 줄\n둘째 줄"), texts(chapter))
        val bold = chapter.blocks.filterIsInstance<ContentBlock.Text>()[1].spans.single()
        assertEquals("굵게", "안쪽 글 굵게.".substring(bold.start, bold.end))
    }

    @Test
    fun putsHangulFirstInRuby() {
        val chapter = book(
            TestEpub().chapter(
                "one.xhtml",
                "<p><ruby>學校<rp>(</rp><rt>학교</rt><rp>)</rp></ruby>에 갔다. <ruby>학생<rt>學生</rt></ruby>이다.</p>"
            )
        ).chapters.single()
        assertEquals(listOf("학교(學校)에 갔다. 학생(學生)이다."), texts(chapter))
    }

    @Test
    fun movesFootnotesToTheEndOfTheChapter() {
        val chapter = book(
            TestEpub().chapter(
                "one.xhtml",
                """<p>본문<a epub:type="noteref" href="#n1">1</a> 계속.</p>
                <aside epub:type="footnote" id="n1"><p>1. 주석 내용.</p></aside>
                <p>다음 문단.</p>
                <section epub:type="endnotes"><h2>미주</h2><ol><li>첫 미주.</li><li>둘째 미주.</li></ol></section>"""
            )
        ).chapters.single()
        assertEquals(
            listOf(
                ContentBlock.Text("본문[1] 계속.", listOf(ContentSpan(2, 5, SpanType.NOTE, "1. 주석 내용."))),
                ContentBlock.Text("다음 문단."),
                ContentBlock.Footer("1. 주석 내용."),
                ContentBlock.Footer("첫 미주."),
                ContentBlock.Footer("둘째 미주.")
            ),
            chapter.blocks
        )
    }

    @Test
    fun findsNotesInOtherDocuments() {
        val chapter = book(
            TestEpub()
                .nav("one.xhtml" to "본문", "notes.xhtml" to "주석")
                .chapter("one.xhtml", """<p>옛말<sup><a href="notes.xhtml#fn1">1)</a></sup>이 나온다. 2024년<a href="#x">3</a>.</p>""")
                .chapter("notes.xhtml", """<p><a id="fn1"></a>1) 옛날에 쓰던 말.</p>""")
        ).chapters.first()
        val text = chapter.blocks.single() as ContentBlock.Text
        assertEquals("옛말[1]이 나온다. 2024년[3].", text.text)
        // A marker whose target can't be found stays plain text
        assertEquals(listOf(ContentSpan(2, 5, SpanType.NOTE, "1) 옛날에 쓰던 말.")), text.spans)
    }

    @Test
    fun pairsRaisedNumbersWithNotesInSmallerType() {
        val chapter = book(
            TestEpub()
                .file(
                    "OEBPS/style.css",
                    """/* 주석 */ .chapter .raised { vertical-align: super; font-size: 0.65em }
                    @media screen { .note { font-size: 0.75em !important; } } .gray { color: #737373 }"""
                )
                .chapter(
                    "one.xhtml",
                    """<div class="chapter"><p>1.</p>
                    <p>본문이다.<span class="raised">1</span> 다음 문장.<sup>2</sup> 끝.<sup>1</sup><sup>2</sup></p>
                    <p class="note gray"><span>1 </span>첫 주석이다.</p>
                    <p>2 문단은 주석이 아니다.</p>
                    <p style="font-size: 80%">2) 둘째 주석이다.</p>
                    <p class="note">12 열둘째 주석이다.</p>
                    <p>넓이는 3m<sup>2</sup>이다.</p></div>""",
                    head = """<link rel="stylesheet" type="text/css" href="style.css"/>"""
                )
        ).chapters.single()
        assertEquals(
            listOf(
                ContentBlock.Text("1."),
                ContentBlock.Text(
                    "본문이다.[1] 다음 문장.[2] 끝.[12]",
                    listOf(
                        ContentSpan(5, 8, SpanType.NOTE, "1 첫 주석이다."),
                        ContentSpan(15, 18, SpanType.NOTE, "2) 둘째 주석이다."),
                        // Split into two raised elements
                        ContentSpan(21, 25, SpanType.NOTE, "12 열둘째 주석이다.")
                    )
                ),
                // Not in smaller type, so not a note
                ContentBlock.Text("2 문단은 주석이 아니다."),
                // Without a note after it
                ContentBlock.Text("넓이는 3m2이다."),
                ContentBlock.Footer("1 첫 주석이다."),
                ContentBlock.Footer("2) 둘째 주석이다."),
                ContentBlock.Footer("12 열둘째 주석이다.")
            ),
            chapter.blocks
        )
    }

    @Test
    fun keepsItalicsAndBoldSetWithStyles() {
        val chapter = book(
            TestEpub()
                .file("OEBPS/style.css", ".chapter .ita { font-style: italic } .b { font-weight: 700 }")
                .chapter(
                    "one.xhtml",
                    """<p>예컨대 <span class="ita">기울인 <span class="ita">글</span></span>과 <span style="font-weight: bold">굵은 글</span>.</p>
                    <div class="ita b"><p>첫 문단</p><p>둘째 문단</p></div>""",
                    head = """<link rel="stylesheet" href="style.css"/>"""
                )
        ).chapters.single()
        assertEquals(
            listOf(
                ContentBlock.Text(
                    "예컨대 기울인 글과 굵은 글.",
                    listOf(ContentSpan(4, 9, SpanType.ITALIC), ContentSpan(11, 15, SpanType.BOLD))
                ),
                // Each paragraph of a styled block
                ContentBlock.Text("첫 문단", listOf(ContentSpan(0, 4, SpanType.ITALIC), ContentSpan(0, 4, SpanType.BOLD))),
                ContentBlock.Text("둘째 문단", listOf(ContentSpan(0, 5, SpanType.ITALIC), ContentSpan(0, 5, SpanType.BOLD)))
            ),
            chapter.blocks
        )
    }

    @Test
    fun keepsBlankLinesAndNewPagesAsBreaks() {
        val chapter = book(
            TestEpub()
                .nav("one.xhtml" to "1장")
                .chapter("one.xhtml", "<p><br/></p><p>제목</p><p><br/></p><p> </p><p>첫 글.</p><p><br/></p>")
                // Not in the table of contents, so it continues the chapter on a new page
                .chapter("two.xhtml", "<p>둘째 글.</p><h2>소제목</h2><p><br/></p><p>셋째 글.</p>")
        ).chapters.single()
        assertEquals(
            listOf(
                ContentBlock.Text("제목"),
                ContentBlock.Break,
                ContentBlock.Text("첫 글."),
                ContentBlock.Break,
                ContentBlock.Text("둘째 글."),
                // A heading sets its part apart already
                ContentBlock.Heading("소제목", 2),
                ContentBlock.Text("셋째 글.")
            ),
            chapter.blocks
        )
    }

    @Test
    fun dropsTitlePagesSplitByBlankLines() {
        val chapter = book(
            TestEpub()
                .nav("one.xhtml" to "1부 ─ 바다")
                .chapter("one.xhtml", "<p>1부</p><p><br/></p><p>바다</p><p><br/></p><p>본문.</p>")
        ).chapters.single()
        assertEquals(listOf(ContentBlock.Text("본문.")), chapter.blocks)
    }

    @Test
    fun turnsTableRowsIntoLines() {
        val chapter = book(
            TestEpub().chapter(
                "one.xhtml",
                "<p>표:</p><table><tr><th>이름</th><th>나이</th></tr><tr><td>철수</td><td>열 살</td></tr></table>"
            )
        ).chapters.single()
        assertEquals(listOf("표:", "이름 · 나이", "철수 · 열 살"), texts(chapter))
    }

    @Test
    fun writesImagesUnderNamesOfItsOwn() {
        val book = book(
            TestEpub()
                .file("OEBPS/images/cover.jpg", TestEpub.PNG)
                .file("OEBPS/images/fig 1.png", TestEpub.PNG)
                .manifestItem("""<item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>""")
                .chapter(
                    "one.xhtml",
                    """<p>그림:</p><figure><img src="images/fig%201.png" width="300" height="200"/><figcaption>설명</figcaption></figure>
                    <p>글자<img src="images/fig%201.png" width="12" height="12"/> 안 그림.</p>
                    <img src="images/missing.png"/>"""
                )
        )
        val image = book.chapters.single().blocks.filterIsInstance<ContentBlock.Image>().single()
        assertEquals(ContentBlock.Image("book-image:1.png", "설명", emptyList(), 300, 200), image)
        assertEquals("2.jpg", book.cover)
        assertEquals(setOf("1.png", "2.jpg"), imageDir.list()!!.toSet())
    }

    @Test
    fun findsAnEpub2Cover() {
        val book = book(
            TestEpub()
                .metadata("""<dc:title>책</dc:title><meta name="cover" content="cover-img"/>""")
                .file("OEBPS/c.png", TestEpub.PNG)
                .manifestItem("""<item id="cover-img" href="c.png" media-type="image/png"/>""")
                .chapter("one.xhtml", "<p>가</p>")
        )
        assertEquals("1.png", book.cover)
        assertNull(book.author)
    }

    @Test
    fun findsACoverPageWithoutMetadata() {
        val book = book(
            TestEpub()
                .file("OEBPS/front.jpg", TestEpub.PNG)
                .chapter("cover.xhtml", """<div><img src="front.jpg"/></div>""", itemref = """linear="no"""")
                .chapter("one.xhtml", "<p>본문.</p>")
        )
        assertEquals("1.jpg", book.cover)
        assertEquals(listOf("본문."), texts(book.chapters.single()))
    }

    @Test
    fun findsTheCoverPageTheGuideNames() {
        val book = book(
            TestEpub()
                .file("OEBPS/front.png", TestEpub.PNG)
                .chapter("one.xhtml", "<p>본문.</p>")
                .chapter("cover.xhtml", """<p>표지</p><img src="front.png"/>""")
                .guide("""<reference type="cover" href="cover.xhtml"/>""")
        )
        assertEquals("1.png", book.cover)
    }

    @Test
    fun doesNotTakeAFirstPageWithTextForACover() {
        val book = book(
            TestEpub()
                .file("OEBPS/pic.png", TestEpub.PNG)
                .chapter("one.xhtml", """<p>본문.</p><img src="pic.png"/>""")
        )
        assertNull(book.cover)
    }

    @Test
    fun acceptsFontObfuscation() {
        val epub = TestEpub()
            .chapter("one.xhtml", "<p>가</p>")
            .file("META-INF/encryption.xml", encryption("http://www.idpf.org/2008/embedding", "OEBPS/font.otf"))
        assertEquals(1, book(epub).chapters.size)
    }

    @Test
    fun refusesCopyProtectedBooks() {
        val encrypted = TestEpub()
            .chapter("one.xhtml", "<p>가</p>")
            .file("META-INF/encryption.xml", encryption("http://www.w3.org/2001/04/xmlenc#aes128-cbc", "OEBPS/one.xhtml"))
        assertEquals(EpubFailure.DRM, failure(encrypted))
        folder.root.listFiles()!!.forEach { it.deleteRecursively() }

        val adobe = TestEpub().chapter("one.xhtml", "<p>가</p>").file("META-INF/rights.xml", "<rights/>")
        assertEquals(EpubFailure.DRM, failure(adobe))
        folder.root.listFiles()!!.forEach { it.deleteRecursively() }

        val lcp = TestEpub().chapter("one.xhtml", "<p>가</p>").file("META-INF/license.lcpl", "{}")
        assertEquals(EpubFailure.DRM, failure(lcp))
    }

    @Test
    fun refusesFixedLayoutBooks() {
        val epub = TestEpub()
            .metadata("""<dc:title>만화</dc:title><meta property="rendition:layout">pre-paginated</meta>""")
            .chapter("one.xhtml", "<p>가</p>")
        assertEquals(EpubFailure.FIXED_LAYOUT, failure(epub))
    }

    @Test
    fun refusesBooksWithoutText() {
        val epub = TestEpub()
            .file("OEBPS/pic.png", TestEpub.PNG)
            .chapter("one.xhtml", """<img src="pic.png"/>""")
        assertEquals(EpubFailure.NO_TEXT, failure(epub))
    }

    @Test
    fun refusesFilesThatAreNotEpubs() {
        assertEquals(EpubFailure.INVALID, failure(TestEpub().withoutPackage().file("readme.txt", "hello")))
        val notZip = folder.newFile("not.epub").apply { writeText("not a zip") }
        assertEquals(EpubFailure.INVALID, (EpubParser().parse(notZip, imageDir) as EpubResult.Failure).reason)
    }

    @Test
    fun stopsReadingAtTheSizeLimits() {
        val big = TestEpub().chapter("one.xhtml", "<p>${"가".repeat(10_000)}</p>")
        assertEquals(
            EpubFailure.TOO_LARGE,
            (parse(big, EpubLimits(maxEntryBytes = 5_000)) as EpubResult.Failure).reason
        )
    }

    @Test
    fun stopsAtTooManyEntries() {
        val epub = TestEpub().chapter("one.xhtml", "<p>가</p>")
        repeat(20) { epub.file("OEBPS/f$it.txt", "x") }
        assertEquals(
            EpubFailure.TOO_LARGE,
            (parse(epub, EpubLimits(maxEntries = 10)) as EpubResult.Failure).reason
        )
    }

    @Test
    fun ignoresPathsOutsideTheBook() {
        // Image files get names of their own, so an entry name that climbs out of the archive
        // can't place a file anywhere, and links that leave the book are ignored
        val book = book(
            TestEpub()
                .file("../evil.png", TestEpub.PNG)
                .chapter("one.xhtml", """<p>가</p><img src="../../evil.png"/><img src="/etc/passwd"/>""")
        )
        assertTrue(book.chapters.single().blocks.none { it is ContentBlock.Image })
        assertFalse(File(folder.root.parentFile, "evil.png").exists())
        assertFalse(imageDir.exists() && imageDir.list()!!.isNotEmpty())
    }

    @Test
    fun doesNotLoadExternalEntities() {
        val secret = folder.newFile("secret.txt").apply { writeText("비밀") }
        val doctype = """<!DOCTYPE html [<!ENTITY xxe SYSTEM "${secret.toURI()}">]>"""
        val epub = TestEpub().chapter("one.xhtml", "")
        epub.file(
            "OEBPS/one.xhtml",
            TestEpub.xhtml("<p>가 &xxe; 나</p>").replaceFirst("<html", "$doctype<html")
        )
        val text = texts(book(epub).chapters.single()).joinToString()
        assertFalse(text.contains("비밀"))
    }

    @Test
    fun splitsVeryLongChapters() {
        val paragraph = "가".repeat(1_000)
        val body = (1..200).joinToString("") { i ->
            if (i == 120) "<h2>중간 제목</h2><p>$paragraph</p>" else "<p>$paragraph</p>"
        }
        val book = book(TestEpub().nav("one.xhtml" to "긴 장").chapter("one.xhtml", body))
        assertEquals(listOf("긴 장", "긴 장 (2)"), book.chapters.map { it.title })
        // The second part starts at the heading, which is past the middle of the first part
        assertEquals(ContentBlock.Heading("중간 제목", 2), book.chapters[1].blocks.first())
        assertTrue(book.chapters.all { chapter ->
            chapter.blocks.sumOf { (it as? ContentBlock.Text)?.text?.length ?: 0 } <= EpubParser.MAX_CHAPTER_CHARS
        })
    }

    private fun encryption(algorithm: String, uri: String) =
        """<?xml version="1.0"?>
        <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
        <enc:EncryptedData><enc:EncryptionMethod Algorithm="$algorithm"/>
        <enc:CipherData><enc:CipherReference URI="$uri"/></enc:CipherData></enc:EncryptedData>
        </encryption>"""
}
