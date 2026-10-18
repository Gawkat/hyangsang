package dev.kettu.hyangsang.analyzer

/** Hangul syllable arithmetic: initial, medial and final indexes as in Unicode. */
internal object Hangul {
    fun isSyllable(c: Char) = c in '가'..'힣'

    fun initial(c: Char) = (c - '가') / 588
    fun medial(c: Char) = (c - '가') % 588 / 28
    fun final(c: Char) = (c - '가') % 28

    fun compose(initial: Int, medial: Int, final: Int = 0): Char = ('가'.code + (initial * 21 + medial) * 28 + final).toChar()

    fun withFinal(c: Char, final: Int) = compose(initial(c), medial(c), final)
    fun withMedial(c: Char, medial: Int) = compose(initial(c), medial, final(c))
    fun withInitial(c: Char, initial: Int) = compose(initial, medial(c), final(c))

    // Initials
    const val I_N = 2 // ㄴ
    const val I_R = 5 // ㄹ
    const val I_B = 7 // ㅂ
    const val I_S = 9 // ㅅ
    const val I_NG = 11 // ㅇ, no sound
    const val I_P = 17 // ㅍ
    const val I_H = 18 // ㅎ

    // Medials
    const val A = 0
    const val AE = 1
    const val YA = 2
    const val YAE = 3
    const val EO = 4
    const val E = 5
    const val YEO = 6
    const val YE = 7
    const val O = 8
    const val WA = 9
    const val WAE = 10
    const val OE = 11
    const val YO = 12
    const val U = 13
    const val WEO = 14
    const val EU = 18
    const val I = 20

    // Finals
    const val G = 1
    const val N = 4
    const val D = 7
    const val L = 8
    const val M = 16
    const val B = 17
    const val S = 19
    const val SS = 20
    const val H = 27

    /** Final consonants an ending can start with, as the dictionary writes them (-ㄴ다, -ㅂ니다). */
    val JAMO_FINALS = mapOf(N to 'ㄴ', L to 'ㄹ', M to 'ㅁ', B to 'ㅂ')

    fun isJamo(c: Char) = c in 'ㄱ'..'ㅣ'

    fun endsInConsonant(s: String) = s.lastOrNull()?.let { isSyllable(it) && final(it) != 0 } == true
    fun endsInL(s: String) = s.lastOrNull()?.let { isSyllable(it) && final(it) == L } == true

    /** Whether a stem takes 아 rather than 어: its last vowel is ㅏ or ㅗ. */
    private val BRIGHT = setOf(A, YA, O, YO)

    fun isBright(stem: String) = stem.lastOrNull()?.let { isSyllable(it) && medial(it) in BRIGHT } == true
}
