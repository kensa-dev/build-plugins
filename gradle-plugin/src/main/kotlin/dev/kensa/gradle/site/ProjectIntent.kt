package dev.kensa.gradle.site

import java.io.File
import java.io.Serializable

/**
 * What a project locally declared on its `kensa { … }` block. Buffered into the
 * [KensaSiteService] from each project's `afterEvaluate` so that, once all projects are
 * evaluated, role assignment is a pure function of the collected intents.
 */
data class ProjectIntent(
    val projectPath: String,
    val isRoot: Boolean,
    val rootProjectName: String,
    val siteEnabled: Boolean,
    val sourceSets: Set<String>,
    val sourceTitles: Map<String, String>,
    val kensaCoreVersion: String,
    val siteRoot: File,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
