package app.xwd.data

/**
 * Pure planning logic for the "download everything" feature, kept free of
 * Android and network types so it can be unit-tested.
 */
object BulkDownload {

    /**
     * Catalog rows that the bulk download would fetch: every listed puzzle
     * from an enabled source that isn't already on the device, newest first.
     */
    fun pending(
        catalog: List<CatalogEntity>,
        savedIds: Set<String>,
        disabledSources: Set<String>,
    ): List<CatalogEntity> =
        catalog
            .filter { it.sourceId !in disabledSources && it.id !in savedIds }
            .sortedWith(compareByDescending<CatalogEntity> { it.sortDate }.thenBy { it.id })
}
