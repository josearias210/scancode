package com.scancode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanLibraryTest {
    private val code = ScanResult("https://example.com/guide", "QR", 1)
    private val travel = ScanCollection("travel", "Travel")
    private val work = ScanCollection("work", "Work")
    private val library = ScanLibrary(collections = listOf(travel, work))

    @Test
    fun addsOneScanToSeveralCollectionsWithoutDuplicatingRecentHistory() {
        val stored = library.setCollections(code, setOf(travel.id, work.id), saveToHistory = true)
            .save(code.copy(time = 2))

        assertEquals(listOf(code), stored.recent)
        assertEquals(setOf(travel.id, work.id), stored.collectionIdsFor(code.value))
        assertEquals(mapOf(code.value to code), stored.collected)
        assertEquals(listOf(code), stored.scansIn(stored.collections.first()))
    }

    @Test
    fun collectedCodesSurviveTheHistoryLimitAndClearingHistory() {
        val saved = library.setCollections(code, setOf(work.id), saveToHistory = true)
        val full = (2L..205L).fold(saved) { state, time -> state.save(ScanResult("Scan $time", "QR", time)) }

        assertEquals(200, full.recent.size)
        assertFalse(full.recent.contains(code))
        val reopened = CollectionsJson.decode(emptyList(), CollectionsJson.encode(full))
        assertTrue(reopened.recent.isEmpty())
        assertEquals(listOf(code), reopened.scansIn(reopened.collections.last()))
    }

    @Test
    fun removingMembershipAndDeletingCollectionPreserveOtherCopies() {
        val saved = library.setCollections(code, setOf(travel.id, work.id), saveToHistory = true)
        val moved = saved.setCollections(code, setOf(work.id), saveToHistory = false)

        assertTrue(moved.scansIn(moved.collections.first()).isEmpty())
        assertEquals(listOf(code), moved.scansIn(moved.collections.last()))

        val afterDelete = moved.deleteCollection(work.id)
        assertEquals(listOf(code), afterDelete.recent)
        assertTrue(afterDelete.collected.isEmpty())
    }

    @Test
    fun emptyOrCorruptCollectionsKeepPriorHistoryReadable() {
        val legacy = HistoryJson.encode(listOf(code))
        for (data in listOf(null, "invalid JSON")) {
            val opened = CollectionsJson.decode(HistoryJson.decode(legacy), data)
            assertEquals(listOf(code), opened.recent)
            assertTrue(opened.collections.isEmpty())
        }
    }

    @Test
    fun editingArchivedMembershipDoesNotRecreateADeletedHistoryEntry() {
        val saved = library.setCollections(code, setOf(travel.id), saveToHistory = true)
        val archived = saved.copy(recent = emptyList())
        val moved = archived.setCollections(code, setOf(work.id), saveToHistory = false)

        assertTrue(moved.recent.isEmpty())
        assertEquals(setOf(work.id), moved.collectionIdsFor(code.value))
    }
}
