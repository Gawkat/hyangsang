package dev.kettu.hyangsang.tools

import com.google.gson.GsonBuilder
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

object DictionaryMapper {
    private val posMap = mapOf(
        "명사" to "Noun",
        "대명사" to "Pronoun",
        "수사" to "Numeral",
        "조사" to "Particle",
        "동사" to "Verb",
        "형용사" to "Adjective",
        "관형사" to "Determiner",
        "부사" to "Adverb",
        "감탄사" to "Interjection",
        "접사" to "Affix",
        "의존 명사" to "Bound Noun",
        "보조 동사" to "Auxiliary Verb",
        "보조 형용사" to "Auxiliary Adjective",
        "어미" to "Ending"
    )

    private val levelMap = mapOf(
        "초급" to "Beginner",
        "중급" to "Intermediate",
        "고급" to "Advanced",
        "없음" to "None"
    )

    private val unitMap = mapOf(
        "단어" to "Word",
        "관용구" to "Idiom",
        "속담" to "Proverb"
    )

    fun mapPos(ko: String?) = posMap[ko] ?: ko
    fun mapLevel(ko: String?) = levelMap[ko] ?: ko
    fun mapUnit(ko: String?) = unitMap[ko] ?: ko
}

fun main(args: Array<String>) {
    val inputDir = File("app/src/main/assets/dictionary")
    val outputFile = File("app/src/main/assets/dictionary.db")

    if (outputFile.exists()) outputFile.delete()

    DriverManager.getConnection("jdbc:sqlite:${outputFile.absolutePath}").use { conn ->
        createTables(conn)

        val entryStmt = conn.prepareStatement(
            "INSERT INTO dictionary_entries (originalId, word, origin, homonymNumber, partOfSpeech, vocabularyLevel, semanticCategory, lexicalUnit, pronunciation, audioUrl) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        val senseStmt = conn.prepareStatement(
            "INSERT INTO dictionary_senses (entryId, definitionKo, definitionEn, translationEn) VALUES (?, ?, ?, ?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        )
        val exampleStmt = conn.prepareStatement(
            "INSERT INTO dictionary_examples (senseId, example, type) VALUES (?, ?, ?)"
        )

        inputDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
            println("Processing ${file.absolutePath}...")
            try {
                parseAndInsert(file, entryStmt, senseStmt, exampleStmt)
            } catch (e: Exception) {
                System.err.println("Error processing ${file.name}: ${e.message}")
                e.printStackTrace()
            }
        }

        conn.commit()
    }
    println("Done! Database created at ${outputFile.absolutePath}")
}

fun createTables(conn: Connection) {
    conn.autoCommit = false
    val statement = conn.createStatement()
    statement.execute(
        """
        CREATE TABLE IF NOT EXISTS dictionary_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            originalId TEXT NOT NULL,
            word TEXT NOT NULL,
            origin TEXT,
            homonymNumber INTEGER NOT NULL,
            partOfSpeech TEXT,
            vocabularyLevel TEXT,
            semanticCategory TEXT,
            lexicalUnit TEXT,
            pronunciation TEXT,
            audioUrl TEXT
        )
    """
    )
    statement.execute(
        """
        CREATE TABLE IF NOT EXISTS dictionary_senses (
            senseId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            entryId INTEGER NOT NULL,
            definitionKo TEXT NOT NULL,
            definitionEn TEXT,
            translationEn TEXT,
            FOREIGN KEY(entryId) REFERENCES dictionary_entries(id) ON DELETE CASCADE
        )
    """
    )
    statement.execute(
        """
        CREATE TABLE IF NOT EXISTS dictionary_examples (
            exampleId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            senseId INTEGER NOT NULL,
            example TEXT NOT NULL,
            type TEXT,
            FOREIGN KEY(senseId) REFERENCES dictionary_senses(senseId) ON DELETE CASCADE
        )
    """
    )
    statement.close()
}

fun parseAndInsert(
    file: File,
    entryStmt: PreparedStatement,
    senseStmt: PreparedStatement,
    exampleStmt: PreparedStatement
) {
    val gson = GsonBuilder()
        .registerTypeAdapterFactory(EnsureListTypeAdapterFactory())
        .create()

    val resource = try {
        file.reader().use { gson.fromJson(it, LexicalResource::class.java) }
    } catch (e: com.google.gson.JsonSyntaxException) {
        println("JSON Syntax Error in ${file.name}: ${e.message}")
        throw e
    } catch (e: IllegalStateException) {
        println("Hierarchy Mismatch in ${file.name}: ${e.message}")
        throw e
    }

    resource.lexicalResource.lexicon.lexicalEntry.forEach { entry ->
        try {
            insertLexicalEntry(entry, entryStmt, senseStmt, exampleStmt)
        } catch (e: Exception) {
            System.err.println("Error processing entry ${entry.valX} in ${file.name}: ${e.message}")
        }
    }
}

fun insertLexicalEntry(
    entry: LexicalEntry,
    entryStmt: PreparedStatement,
    senseStmt: PreparedStatement,
    exampleStmt: PreparedStatement
) {
    val id = entry.valX

    var word = ""
    var origin: String? = null

    // Lemma feat handling
    for (lemma in entry.lemma) {
        if (lemma.feat.att == "writtenForm") {
            word = lemma.feat.valX
        }
    }
    for (lemma in entry.lemma) {
        if (lemma.feat.att == "origin") {
            origin = lemma.feat.valX
        }
    }

    // Check if origin is in entry level feats
    if (origin == null) {
        origin = entry.feat.findFeat("origin")
    }

    if (word.isEmpty()) {
        System.err.println("Warning: Entry $id has no writtenForm")
    }

    val homonymNumber = entry.feat.findFeat("homonym_number")?.toIntOrNull() ?: 0
    val pos = DictionaryMapper.mapPos(entry.feat.findFeat("partOfSpeech"))
    val level = DictionaryMapper.mapLevel(entry.feat.findFeat("vocabularyLevel"))
    val category = entry.feat.findFeat("semanticCategory")
    val lexicalUnit = DictionaryMapper.mapUnit(entry.feat.findFeat("lexicalUnit"))

    var pronunciation = ""
    var audioUrl = ""

    if (entry.wordForm != null) {
        for (wordForm in entry.wordForm) {
            if (wordForm.feat.findFeat("pronunciation") != null) {
                pronunciation = wordForm.feat.findFeat("pronunciation")!!
            }
        }
        for (wordForm in entry.wordForm) {
            if (wordForm.feat.findFeat("sound") != null) {
                audioUrl = wordForm.feat.findFeat("sound")!!
            }
        }
    }

    // Insert Entry
    entryStmt.setString(1, id)
    entryStmt.setString(2, word)
    entryStmt.setString(3, origin)
    entryStmt.setInt(4, homonymNumber)
    entryStmt.setString(5, pos)
    entryStmt.setString(6, level)
    entryStmt.setString(7, category)
    entryStmt.setString(8, lexicalUnit)
    entryStmt.setString(9, pronunciation)
    entryStmt.setString(10, audioUrl)

    var entryDbId: Long = -1
    try {
        entryStmt.executeUpdate()
        val generatedKeys = entryStmt.generatedKeys
        if (generatedKeys.next()) {
            entryDbId = generatedKeys.getLong(1)
        }
    } catch (e: Exception) {
        System.err.println("Error inserting entry $id: ${e.message}")
        return
    }

    if (entryDbId == -1L) return

    // Insert Senses
    entry.sense.forEach { sense ->
        val definitionKo = sense.feat.findFeat("definition") ?: ""

        var definitionEn: String? = null
        var translationEn: String? = null

        if (sense.equivalent == null) {
            return@forEach
        }

        sense.equivalent.forEach { eq ->
            if (eq.feat.findFeat("language") == "영어") {
                definitionEn = eq.feat.findFeat("definition")
                translationEn = eq.feat.findFeat("lemma")
            }
        }

        senseStmt.setLong(1, entryDbId)
        senseStmt.setString(2, definitionKo)
        senseStmt.setString(3, definitionEn)
        senseStmt.setString(4, translationEn)
        senseStmt.executeUpdate()

        val generatedKeys = senseStmt.generatedKeys
        if (generatedKeys.next()) {
            if (sense.senseExample == null) {
                return@forEach
            }

            val senseId = generatedKeys.getLong(1)

            sense.senseExample.forEach { ex ->
                val text = ex.feat.findFeat("example") ?: ""
                val type = ex.feat.findFeat("type")

                exampleStmt.setLong(1, senseId)
                exampleStmt.setString(2, text)
                exampleStmt.setString(3, type)
                exampleStmt.executeUpdate()
            }
        }
    }

    if (entry.sense.isEmpty()) {
        System.err.println("Warning: Entry $id has no senses")
    }
}

fun List<Feat>.findFeat(att: String): String? = find { it.att == att }?.valX
