package com.novaplay.tv.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactCategorySelectorTest {

    @Test
    fun smallCategoryListsKeepDirectChips() {
        assertFalse(shouldUseCategoryPicker(0))
        assertFalse(shouldUseCategoryPicker(8))
    }

    @Test
    fun largeCategoryListsUseScrollablePicker() {
        assertTrue(shouldUseCategoryPicker(9))
        assertTrue(shouldUseCategoryPicker(120))
    }

    @Test
    fun categoryFilterIsCaseInsensitiveAndKeepsOrder() {
        val categories = listOf(
            1L to "France News",
            2L to "Morocco Sports",
            3L to "French Movies",
        )

        assertEquals(
            listOf(1L to "France News", 3L to "French Movies"),
            filterProviderCategories(categories, "fr"),
        )
    }

    @Test
    fun blankFilterReturnsAllCategories() {
        val categories = listOf(1L to "One", 2L to "Two")
        assertEquals(categories, filterProviderCategories(categories, "   "))
    }
}
