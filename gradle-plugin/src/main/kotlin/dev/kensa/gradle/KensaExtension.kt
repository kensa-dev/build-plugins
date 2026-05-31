package dev.kensa.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

abstract class KensaExtension @Inject constructor(layout: ProjectLayout) {
    abstract val enabled: Property<Boolean>
    abstract val debug: Property<Boolean>

    /**
     * Source sets the Kensa Kotlin compiler plugin instruments. Set to `emptySet()` if no source
     * code in this project uses the expandable-sentence / rendered-value features that the
     * compiler plugin powers. Set to `setOf("main")` if only the production source has such
     * support code. Defaults to `setOf("test")`.
     *
     * Independent of [outputSourceSets] — instrumenting a source set does not by itself mean
     * a Test task should emit Kensa output for it.
     */
    abstract val sourceSets: SetProperty<String>

    /**
     * Source sets whose Test tasks emit Kensa output: the on-disk reports under
     * `build/kensa` (HTML, JSON indices) and the `Kensa Output : …` banner. In site mode,
     * these are also the source sets whose Test tasks contribute per-source bundles to the
     * assembled site. Defaults to `setOf("test")`.
     *
     * Independent of [sourceSets]. A source set listed here gets `dev.kensa:kensa-core` on its
     * runtime classpath even if the Kotlin compiler plugin is not applied to it.
     */
    abstract val outputSourceSets: SetProperty<String>

    abstract val site: Property<Boolean>
    abstract val siteRoot: DirectoryProperty

    /**
     * Version of dev.kensa:kensa-core (and dev.kensa:kensa-compiler-plugin) to resolve at
     * compile + task time. Defaults to the version this plugin release was tested against
     * (`KENSA_CORE_VERSION`). Override to pin a different kensa-core within the supported
     * range — see the compatibility matrix in the plugin README.
     */
    abstract val kensaCoreVersion: Property<String>

    /**
     * Per-source display labels for site mode, keyed by source id. Entries set here override
     * whatever the test runtime wrote to that source's `configuration.json` (including a value
     * `Kensa.konfigure { titleText = ... }` may have set in code). When a source id has no entry,
     * the per-source `titleText` from the test runtime is used unchanged.
     *
     * ```kotlin
     * kensa {
     *     sourceTitles.put("uiTest", "UI Tests")
     *     sourceTitles.put("acceptanceTest", "Acceptance Tests")
     * }
     * ```
     */
    abstract val sourceTitles: MapProperty<String, String>

    init {
        enabled.convention(true)
        debug.convention(false)
        sourceSets.convention(setOf("test"))
        outputSourceSets.convention(setOf("test"))
        site.convention(false)
        siteRoot.convention(layout.buildDirectory.dir("kensa-site"))
        kensaCoreVersion.convention(KENSA_CORE_VERSION)
        sourceTitles.convention(emptyMap())
    }

    /**
     * Member-extension that lets users write `sourceTitles["uiTest"] = "UI Tests"` inside the
     * `kensa { … }` block — map-literal feel without losing `MapProperty`'s lazy-Provider
     * semantics. Defined as a member (not a top-level extension) so it's resolved through the
     * `KensaExtension` implicit receiver without users needing an explicit import in their
     * build script.
     */
    operator fun MapProperty<String, String>.set(key: String, value: String) {
        put(key, value)
    }
}
