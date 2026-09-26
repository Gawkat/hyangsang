package dev.kettu.hyangsang.data.defaults

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DefaultCategoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun Context.inLocale(locale: Locale): Context = createConfigurationContext(
        Configuration(resources.configuration).apply { setLocale(locale) }
    )

    @Test
    fun categoriesAreFoundByTheirNameInEveryAppLanguage() {
        val byLabel = DefaultCategory.byLabel(context)
        assertEquals(DefaultCategory.SPORTS, byLabel["Sports"])
        assertEquals(DefaultCategory.SPORTS, byLabel["스포츠"])
        assertEquals(DefaultCategory.ECONOMY, byLabel["경제"])
        assertEquals(null, byLabel["My feeds"])
    }

    @Test
    fun defaultNamesAreTranslatedIntoTheAppLanguage() {
        val english = DefaultData.resolveFeeds(context.inLocale(Locale.ENGLISH))
        val korean = context.inLocale(Locale.KOREA)
        assertEquals(DefaultData.resolveFeeds(korean), DefaultData.localizeFeeds(korean, english))
    }

    @Test
    fun namesTheUserChangedAreKept() {
        val korean = context.inLocale(Locale.KOREA)
        val sports = DefaultData.resolveFeeds(context.inLocale(Locale.ENGLISH))
            .first { it.url.endsWith("/sports.xml") }

        val renamed = sports.copy(title = "K리그")
        assertEquals(listOf(renamed.copy(category = "스포츠")), DefaultData.localizeFeeds(korean, listOf(renamed)))

        val custom = sports.copy(category = "My feeds")
        assertEquals("My feeds", DefaultData.localizeFeeds(korean, listOf(custom)).single().category)

        assertEquals(emptyList<Any>(), DefaultData.localizeFeeds(korean, DefaultData.resolveFeeds(korean)))
    }
}
