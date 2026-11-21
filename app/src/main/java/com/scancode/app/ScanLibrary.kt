package com.scancode.app

internal data class ScanCollection(
    val id: String,
    val name: String,
    val entries: Set<String> = emptySet(),
)

internal data class ScanLibrary(
    val recent: List<ScanResult> = emptyList(),
    val collections: List<ScanCollection> = emptyList(),
    val collected: Map<String, ScanResult> = emptyMap(),
) {
    fun scansIn(collection: ScanCollection): List<ScanResult> =
        collection.entries.mapNotNull(collected::get).sortedByDescending { it.time }

    fun collectionIdsFor(value: String): Set<String> =
        collections.filter { value in it.entries }.mapTo(mutableSetOf()) { it.id }

    fun save(result: ScanResult): ScanLibrary =
        if (recent.any { it.value == result.value }) this
        else copy(recent = (listOf(result) + recent).take(HistoryJson.MAX_ENTRIES))

    fun setCollections(result: ScanResult, ids: Set<String>, saveToHistory: Boolean): ScanLibrary {
        val selected = collections.any { it.id in ids }
        val updated = collections.map { collection ->
            val entries = if (collection.id in ids) collection.entries + result.value
            else collection.entries - result.value
            collection.copy(entries = entries)
        }
        val existing = collected[result.value] ?: recent.firstOrNull { it.value == result.value }
        val archive = if (selected) collected + (result.value to (existing ?: result)) else collected
        val library = if (saveToHistory && selected) save(result) else this
        return library.withCollections(updated, archive)
    }

    fun deleteCollection(id: String): ScanLibrary =
        withCollections(collections.filterNot { it.id == id }, collected)

    private fun withCollections(
        updated: List<ScanCollection>,
        archive: Map<String, ScanResult>,
    ): ScanLibrary {
        val retained = updated.flatMapTo(mutableSetOf()) { it.entries }
        return copy(collections = updated, collected = archive.filterKeys { it in retained })
    }
}
