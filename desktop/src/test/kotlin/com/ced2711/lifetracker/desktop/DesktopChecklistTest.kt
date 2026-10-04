package com.ced2711.lifetracker.desktop

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The daily checklist on the PC: kept in the encrypted data, ticks per day, reachable by assistants. */
class DesktopChecklistTest {
    private val root: File = Files.createTempDirectory("life-assistant-checklist").toFile()
    private val store = DesktopDataStore(root)
    private val today = LocalDate.of(2026, 10, 4)
    private val day = today.toEpochDay()
    private val tools = DesktopAgentTools(store, today = { today })

    @Before
    fun setUp() = runBlocking {
        assertTrue(store.open("checklist-test-password".toCharArray()))
        Unit
    }

    @After
    fun tearDown() {
        store.close()
        root.deleteRecursively()
    }

    private fun items() = store.currentSnapshot()!!.checklistItems.sortedBy { it.sortOrder }

    private fun call(name: String, allowChanges: Boolean = true, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}): JsonObject =
        runBlocking { tools.call(name, buildJsonObject(build), allowChanges).jsonObject }

    @Test
    fun itemsAreAddedRenamedReorderedAndRemovedAndSurviveReopening() = runBlocking {
        store.addChecklistItem("Brush teeth")
        store.addChecklistItem("  Shower  ")
        store.addChecklistItem("   ")
        assertEquals(listOf("Brush teeth", "Shower"), items().map { it.title })

        val shower = items().last()
        store.renameChecklistItem(shower.id, "Shower and stretch")
        store.reorderChecklist(listOf(shower.id))
        assertEquals(listOf("Shower and stretch", "Brush teeth"), items().map { it.title })

        store.setChecklistChecked(shower.id, day, true)
        store.close()
        assertTrue(store.open("checklist-test-password".toCharArray()))
        assertEquals(listOf("Shower and stretch", "Brush teeth"), items().map { it.title })
        assertEquals(listOf(shower.id), store.currentSnapshot()!!.checklistChecks.filter { it.epochDay == day }.map { it.itemId })

        store.deleteChecklistItem(shower.id)
        assertEquals(listOf("Brush teeth"), items().map { it.title })
        assertTrue("its ticks go with it", store.currentSnapshot()!!.checklistChecks.isEmpty())
    }

    @Test
    fun ticksCountForTheirDayOnlyAndOldOnesAreLetGo() = runBlocking {
        store.addChecklistItem("Brush teeth")
        val id = items().single().id
        store.setChecklistChecked(id, day - 40, true)
        store.setChecklistChecked(id, day - 1, true)
        store.setChecklistChecked(id, day, true)
        store.setChecklistChecked(id, day, true)
        val ticks = store.currentSnapshot()!!.checklistChecks
        assertEquals("yesterday's tick does not tick today twice", listOf(day - 1, day), ticks.map { it.epochDay }.sorted())

        store.setChecklistChecked(id, day, false)
        assertEquals(listOf(day - 1), store.currentSnapshot()!!.checklistChecks.map { it.epochDay })
    }

    @Test
    fun assistantsCanReadTickAddAndRemoveChecklistItems() {
        call("add_checklist_item") { put("title", "Brush teeth") }
        call("add_checklist_item") { put("title", "Check homework") }
        val again = call("add_checklist_item") { put("title", "brush teeth") }
        assertEquals("nothing", again["saved"]!!.jsonPrimitive.content)

        call("check_checklist_item") { put("title", "Check Homework") }
        val list = call("get_checklist")
        assertEquals(2, list["total"]!!.jsonPrimitive.int)
        assertEquals(1, list["done"]!!.jsonPrimitive.int)
        val homework = list["items"]!!.jsonArray.map { it.jsonObject }.single { it["title"]!!.jsonPrimitive.content == "Check homework" }
        assertTrue(homework["done"]!!.jsonPrimitive.boolean)

        val overview = call("get_overview")
        assertEquals(1, overview["daily_checklist_done"]!!.jsonPrimitive.int)
        assertEquals(2, overview["daily_checklist_total"]!!.jsonPrimitive.int)

        call("check_checklist_item") { put("id", homework["id"]!!.jsonPrimitive.content.toLong()); put("done", false) }
        assertEquals(0, call("get_checklist")["done"]!!.jsonPrimitive.int)

        call("remove_checklist_item") { put("title", "Brush teeth") }
        assertEquals(listOf("Check homework"), items().map { it.title })
        assertFalse(DesktopAgentTools.TOOLS.single { it.name == "remove_checklist_item" }.readOnly)

        try {
            call("check_checklist_item", allowChanges = false) { put("title", "Check homework") }
            fail("Ticking is a change and must be refused when only reading is allowed")
        } catch (expected: DesktopAgentException) {
            assertTrue(expected.message!!.contains("turned off"))
        }
    }
}
