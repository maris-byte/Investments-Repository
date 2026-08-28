package com.marisbyte.invest.assistant

import com.marisbyte.invest.assistant.model.AssistantTask
import com.marisbyte.invest.assistant.task.TaskMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskMatcherTest {

    private val aufgaben = listOf(
        AssistantTask(id = 1, text = "Zahnarzttermin verschieben"),
        AssistantTask(id = 2, text = "Milch und Butter kaufen"),
        AssistantTask(id = 3, text = "Steuererklärung abgeben"),
        AssistantTask(id = 4, text = "Alte Aufgabe", done = true)
    )

    private fun treffer(query: String): Long? = TaskMatcher.findBest(aufgaben, query)?.id

    @Test
    fun `findet die Aufgabe ueber ein Stichwort`() {
        assertEquals(1L, treffer("Zahnarzt"))
        assertEquals(2L, treffer("Milch"))
        assertEquals(3L, treffer("Steuererklärung"))
    }

    @Test
    fun `findet die Aufgabe trotz Umlauten und Grossschreibung`() {
        assertEquals(3L, treffer("steuererklaerung"))
        assertEquals(3L, treffer("STEUERERKLÄRUNG ABGEBEN"))
    }

    @Test
    fun `findet die Aufgabe ueber mehrere Woerter`() {
        assertEquals(2L, treffer("die Milch kaufen"))
        assertEquals(1L, treffer("den Zahnarzttermin"))
    }

    @Test
    fun `raet nicht ins Blaue`() {
        assertNull(treffer("Fahrrad reparieren"))
        assertNull(treffer(""))
        assertNull(treffer("der die das"))
    }

    @Test
    fun `beachtet erledigte Aufgaben nicht`() {
        assertNull(treffer("Alte Aufgabe"))
        assertNull(TaskMatcher.findBest(emptyList(), "Zahnarzt"))
    }
}
