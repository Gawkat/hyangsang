package dev.kettu.hyangsang.data.local

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class DictionaryAssetTest {

    // If the asset is stamped with another version, Room treats the fresh copy as needing a
    // migration and destructively empties it
    @Test
    fun `bundled dictionary version matches DictionaryDatabase`() {
        val dictionary = File("src/main/assets/dictionary.db")
        assumeTrue("The bundled dictionary is missing", dictionary.exists())

        val version = DriverManager.getConnection("jdbc:sqlite:${dictionary.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { it.getInt(1) }
            }
        }

        assertEquals(DICTIONARY_VERSION, version)
    }
}
