package dev.kensa.gradle.site

import java.io.Serializable

/**
 * A single source set declared by a contributing project, as seen by the aggregator.
 *
 * `namespacedId` is the manifest id (e.g. `"web__test"`) and `sourceSetName` is the contributor's
 * local source-set name (e.g. `"test"`). `localTitle` is the contributor's
 * `kensa.sourceTitles[sourceSetName]` if any — the aggregator can override it via its own
 * `kensa.sourceTitles[namespacedId]`.
 */
data class SourceRegistration(
    val projectPath: String,
    val sourceSetName: String,
    val namespacedId: String,
    val localTitle: String?,
    val kensaCoreVersion: String,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
