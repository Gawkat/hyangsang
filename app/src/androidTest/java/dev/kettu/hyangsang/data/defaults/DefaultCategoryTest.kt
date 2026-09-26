package dev.kettu.hyangsang.data.defaults

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultCategoryTest {

    @Test
    fun categoriesAreFoundByTheirNameInEveryAppLanguage() {
        val byLabel = DefaultCategory.byLabel(InstrumentationRegistry.getInstrumentation().targetContext)
        assertEquals(DefaultCategory.SPORTS, byLabel["Sports"])
        assertEquals(DefaultCategory.SPORTS, byLabel["스포츠"])
        assertEquals(DefaultCategory.ECONOMY, byLabel["경제"])
        assertEquals(null, byLabel["My feeds"])
    }
}
