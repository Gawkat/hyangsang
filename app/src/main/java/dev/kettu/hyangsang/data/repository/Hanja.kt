package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.local.dao.SenseWithExamples
import dev.kettu.hyangsang.data.local.entity.DictionaryEntry
import dev.kettu.hyangsang.data.local.entity.DictionarySense
import java.text.Normalizer

/** What news writes in hanja, beyond what the dictionary's origins cover. */
internal object Hanja {
    // CJK ideographs (北, 韓美): extension A, the unified block and compatibility ideographs
    val RUN = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]+")

    /**
     * Korean text often uses compatibility ideographs (U+F90A for 金), which the dictionary
     * writes as the unified ones (U+91D1). NFC maps one to the other.
     */
    fun normalize(hanja: String): String = Normalizer.normalize(hanja, Normalizer.Form.NFC)

    /**
     * Hanja that news writes for a whole word, mapped to that word's origin in the dictionary
     * (Latin for some loanwords). Hanja whose own entry has the news meaning, like 前 and 核, are
     * left out.
     */
    val NEWS_ABBREVIATIONS = mapOf(
        "韓" to "韓國", "美" to "美國", "中" to "中國", "日" to "日本", "北" to "北韓",
        "英" to "英國", "獨" to "獨逸", "佛" to "France", "露" to "Russia", "伊" to "Italia",
        "加" to "Canada", "印" to "印度", "濠" to "濠洲", "臺" to "臺灣", "歐" to "Europe",
        "與" to "與黨", "野" to "野黨", "檢" to "檢察", "警" to "警察"
    )

    /**
     * Common family names, with their Hangul and usual romanization. News writes a politician's
     * family name in hanja before their title (李대통령, 韓 총리), and the dictionary has no
     * entries for names.
     */
    private val SURNAMES = mapOf(
        "金" to ("김" to "Kim"), "李" to ("이" to "Lee"), "朴" to ("박" to "Park"),
        "崔" to ("최" to "Choi"), "鄭" to ("정" to "Jeong"), "丁" to ("정" to "Jeong"),
        "姜" to ("강" to "Kang"), "趙" to ("조" to "Cho"), "曺" to ("조" to "Cho"),
        "尹" to ("윤" to "Yoon"), "張" to ("장" to "Jang"), "林" to ("임" to "Lim"),
        "韓" to ("한" to "Han"), "吳" to ("오" to "Oh"), "徐" to ("서" to "Seo"),
        "申" to ("신" to "Shin"), "辛" to ("신" to "Shin"), "權" to ("권" to "Kwon"),
        "黃" to ("황" to "Hwang"), "安" to ("안" to "Ahn"), "宋" to ("송" to "Song"),
        "柳" to ("유" to "Yoo"), "劉" to ("유" to "Yoo"), "兪" to ("유" to "Yoo"),
        "洪" to ("홍" to "Hong"), "全" to ("전" to "Jeon"), "高" to ("고" to "Ko"),
        "文" to ("문" to "Moon"), "孫" to ("손" to "Son"), "梁" to ("양" to "Yang"),
        "裵" to ("배" to "Bae"), "白" to ("백" to "Baek"), "許" to ("허" to "Heo"),
        "南" to ("남" to "Nam"), "沈" to ("심" to "Shim"), "盧" to ("노" to "Roh"),
        "河" to ("하" to "Ha"), "郭" to ("곽" to "Kwak"), "成" to ("성" to "Sung"),
        "車" to ("차" to "Cha"), "朱" to ("주" to "Joo"), "禹" to ("우" to "Woo"),
        "具" to ("구" to "Koo"), "羅" to ("나" to "Na"), "閔" to ("민" to "Min"),
        "秋" to ("추" to "Choo"), "千" to ("천" to "Cheon"), "嚴" to ("엄" to "Eom"),
        "元" to ("원" to "Won"), "蔡" to ("채" to "Chae"), "玄" to ("현" to "Hyun"),
        "卞" to ("변" to "Byun"), "邊" to ("변" to "Byun"), "廉" to ("염" to "Yeom"),
        "孟" to ("맹" to "Maeng"), "奇" to ("기" to "Ki"), "潘" to ("반" to "Ban")
    )

    /** Words that follow a family name in news: titles, and 측 or 정부 for someone's side. */
    val TITLES = setOf(
        "대통령", "전", "총리", "부총리", "대표", "원내대표", "장관", "의원", "위원장", "의장",
        "후보", "당선인", "지사", "시장", "회장", "감독", "대변인", "실장", "비서실장", "총장",
        "청장", "대행", "특검", "여사", "씨", "측", "정부", "정권", "체제", "지도부", "캠프"
    )

    fun isSurname(hanja: String) = hanja in SURNAMES

    /** An entry for a family name, in the dictionary's shape so the overlay can show it. */
    fun surnameEntry(hanja: String): DictionaryWithSenses? {
        val (hangul, romanized) = SURNAMES[hanja] ?: return null
        // Negative ids can't clash with the dictionary's
        val id = -hanja.codePointAt(0).toLong()
        return DictionaryWithSenses(
            entry = DictionaryEntry(
                id = id,
                originalId = "surname:$hanja",
                word = hangul,
                origin = hanja,
                partOfSpeech = "Surname",
                vocabularyLevel = null,
                semanticCategory = null,
                lexicalUnit = null,
                pronunciation = null,
                audioUrl = null
            ),
            senses = listOf(
                SenseWithExamples(
                    sense = DictionarySense(
                        senseId = id,
                        entryId = id,
                        definitionKo = "성(姓)의 하나.",
                        definitionEn = "A Korean family name.",
                        translationEn = romanized
                    ),
                    examples = emptyList()
                )
            )
        )
    }
}
