package com.geozelot.homer.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tree the folder picker walks, folded out of the book paths Homer already holds.
 *
 * Only folders that CONTAIN books appear — a scope naming an empty folder would be a rule about
 * nothing — and each carries the size of its whole subtree, because that is the number that answers
 * "is this the level I meant".
 */
class FolderPickerTest {

    private val library = listOf(
        "Pratchett/Discworld/Rincewind/The Colour of Magic",
        "Pratchett/Discworld/Rincewind/Sourcery",
        "Pratchett/Discworld/Death/Mort",
        "Pratchett/Standalones/Nation",
        "Corey/The Expanse/Leviathan Wakes",
        "Loose Book",
    )

    @Test
    fun `the root lists the top-level folders only`() {
        assertEquals(listOf("Corey", "Pratchett"), levelOf(library, "").children.map { it.first })
    }

    @Test
    fun `a book sitting at the root contributes no folder`() {
        assertTrue(levelOf(library, "").children.none { it.first == "Loose Book" })
    }

    @Test
    fun `counts are of the whole subtree, not the immediate folder`() {
        // Pratchett holds four books across three folders, and that is the number that answers
        // "is this the level I meant".
        assertEquals(4, levelOf(library, "").children.single { it.first == "Pratchett" }.second)
    }

    @Test
    fun `descending narrows to that folder`() {
        val level = levelOf(library, "Pratchett")
        assertEquals(listOf("Discworld", "Standalones"), level.children.map { it.first })
        assertEquals(3, level.children.single { it.first == "Discworld" }.second)
    }

    @Test
    fun `a folder holding only books has no children`() {
        assertTrue(levelOf(library, "Pratchett/Discworld/Rincewind").children.isEmpty())
    }

    @Test
    fun `a sibling that merely starts the same way is not included`() {
        val shadowed = library + "PratchettAnthologies/Collected/Something"
        assertEquals(
            listOf("Discworld", "Standalones"),
            levelOf(shadowed, "Pratchett").children.map { it.first },
        )
    }

    @Test
    fun `leading and trailing slashes on the prefix do not matter`() {
        assertEquals(levelOf(library, "Pratchett").children, levelOf(library, "/Pratchett/").children)
    }

    @Test
    fun `children are listed case-insensitively by name`() {
        val mixed = listOf("beta/x/1", "Alpha/y/1", "Gamma/z/1")
        assertEquals(listOf("Alpha", "beta", "Gamma"), levelOf(mixed, "").children.map { it.first })
    }
}
