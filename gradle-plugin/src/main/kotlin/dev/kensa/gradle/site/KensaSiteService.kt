package dev.kensa.gradle.site

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.ConcurrentHashMap

/**
 * Cross-project registry for kensa site mode.
 *
 * Each project that applies `dev.kensa.gradle-plugin` buffers its [ProjectIntent] here during its
 * `afterEvaluate`. Once all projects are evaluated, [materializeRoles] assigns each project an
 * [Role] based on the decision rule:
 *
 * - Root with `site = true` AND ≥1 non-root contributor with `site = true` → **Aggregator**
 * - Non-root with `site = true`, root is Aggregator → **Contributor**
 * - Any project with `site = true`, no aggregator on root → **Standalone**
 * - Otherwise → **None**
 *
 * The aggregator's `assembleKensaSite` task consumes the registrations via `@ServiceReference`,
 * so manifest changes (e.g. a subproject coming or going) invalidate the cache through the
 * service's input contribution.
 */
abstract class KensaSiteService : BuildService<BuildServiceParameters.None> {

    private val intents = ConcurrentHashMap<String, ProjectIntent>()
    private val roles = ConcurrentHashMap<String, Role>()

    @Volatile
    private var materialized: Boolean = false

    fun bufferIntent(intent: ProjectIntent) {
        intents[intent.projectPath] = intent
    }

    @Synchronized
    fun materializeRoles() {
        if (materialized) return
        materialized = true

        val rootIntent = intents.values.firstOrNull { it.isRoot && it.siteEnabled }
        val contributors = intents.values.filter { !it.isRoot && it.siteEnabled }

        if (rootIntent != null && contributors.isNotEmpty()) {
            roles[rootIntent.projectPath] = Role.Aggregator
            for (c in contributors) roles[c.projectPath] = Role.Contributor
            for (other in intents.values) {
                if (!roles.containsKey(other.projectPath) && other.siteEnabled) {
                    // site=true on a project that isn't root and isn't classed as contributor —
                    // can't happen given the predicate above, but guard for completeness.
                    roles[other.projectPath] = Role.Standalone
                }
            }
        } else {
            for (intent in intents.values) {
                if (intent.siteEnabled) roles[intent.projectPath] = Role.Standalone
            }
        }
    }

    fun roleFor(projectPath: String): Role = roles[projectPath] ?: Role.None

    fun aggregatorIntent(): ProjectIntent? =
        intents.values.firstOrNull { roles[it.projectPath] == Role.Aggregator }

    fun contributorIntents(): List<ProjectIntent> =
        intents.values.filter { roles[it.projectPath] == Role.Contributor }
            .sortedBy { it.projectPath }

    /**
     * Returns the namespaced source registrations across all contributors plus the aggregator's
     * own output source sets (if any). The aggregator's own source sets are namespaced using its
     * root project name, mirroring what a contributor would do for `:`. Iterates each intent's
     * [ProjectIntent.outputSourceSets] — only source sets that actually emit Kensa output are
     * registered into the site manifest.
     */
    fun registrations(): List<SourceRegistration> {
        val agg = aggregatorIntent() ?: return emptyList()
        val rootSlug = NamespacedId.slug(agg.projectPath, rootProjectName = agg.rootProjectName)
        val aggregatorRegs = agg.outputSourceSets.map { ss ->
            SourceRegistration(
                projectPath = agg.projectPath,
                sourceSetName = ss,
                namespacedId = NamespacedId.format(rootSlug, ss),
                localTitle = agg.sourceTitles[ss],
                kensaCoreVersion = agg.kensaCoreVersion,
            )
        }
        val contributorRegs = contributorIntents().flatMap { c ->
            val slug = NamespacedId.slug(c.projectPath, rootProjectName = c.rootProjectName)
            c.outputSourceSets.map { ss ->
                SourceRegistration(
                    projectPath = c.projectPath,
                    sourceSetName = ss,
                    namespacedId = NamespacedId.format(slug, ss),
                    localTitle = c.sourceTitles[ss],
                    kensaCoreVersion = c.kensaCoreVersion,
                )
            }
        }
        return (aggregatorRegs + contributorRegs).sortedBy { it.namespacedId }
    }

    /** All intents currently buffered, by project path. Snapshot — not live. */
    fun intentsSnapshot(): Map<String, ProjectIntent> = intents.toMap()
}
