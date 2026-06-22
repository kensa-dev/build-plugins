package dev.kensa.gradle

import dev.kensa.gradle.site.KensaSiteService
import dev.kensa.gradle.site.NamespacedId
import dev.kensa.gradle.site.ProjectIntent
import dev.kensa.gradle.site.Role
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.Bundling
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KensaGradlePlugin : KotlinCompilerPluginSupportPlugin {

    // Captured in `apply` so `getPluginArtifact` (called by the Kotlin Gradle plugin without
    // a Project parameter) can read the per-project KensaExtension to honor an override.
    private lateinit var capturedProject: Project

    override fun apply(target: Project) {
        capturedProject = target

        target.plugins.withType(KotlinBasePlugin::class.java) { kotlinPlugin ->
            val applied = kotlinPlugin.pluginVersion
            if (GradleVersion.version(applied) < GradleVersion.version(MIN_KOTLIN_VERSION)) {
                throw GradleException(
                    "dev.kensa.gradle-plugin requires Kotlin >= $MIN_KOTLIN_VERSION but the project applies Kotlin $applied. " +
                            "Update the Kotlin plugin version."
                )
            }
        }

        target.extensions.create("kensa", KensaExtension::class.java)

        val siteService: Provider<KensaSiteService> = target.gradle.sharedServices.registerIfAbsent(
            "kensa-site-service",
            KensaSiteService::class.java,
        ) {}

        target.afterEvaluate { project ->
            val extension = project.kensaExtension
            checkKensaCoreCompat(extension.kensaCoreVersion.get())
            if (!extension.enabled.get()) return@afterEvaluate

            val isRoot = project == project.rootProject
            if (!isRoot) {
                for (key in extension.sourceTitles.get().keys) {
                    if (key.contains(NamespacedId.SEPARATOR)) {
                        throw GradleException(
                            "Kensa: source title key '$key' on ${project.path} contains the reserved separator '${NamespacedId.SEPARATOR}'. " +
                                "Use a different source-set name, or set the title on the root via kensa.sourceTitles[\"<slug>${NamespacedId.SEPARATOR}<sourceSet>\"]."
                        )
                    }
                }
            }

            wireKensaCoreOnOutputOnlySourceSets(project, extension)

            siteService.get().bufferIntent(
                ProjectIntent(
                    projectPath = project.path,
                    isRoot = isRoot,
                    rootProjectName = project.rootProject.name,
                    siteEnabled = extension.site.get(),
                    sourceSets = extension.sourceSets.get(),
                    outputSourceSets = extension.outputSourceSets.get(),
                    sourceTitles = extension.sourceTitles.get(),
                    kensaCoreVersion = extension.kensaCoreVersion.get(),
                    siteRoot = extension.siteRoot.get().asFile,
                )
            )
        }

        target.gradle.projectsEvaluated {
            val svc = siteService.get()
            svc.materializeRoles()
            when (svc.roleFor(target.path)) {
                Role.Aggregator -> setUpAggregator(target, svc, siteService)
                Role.Contributor -> setUpContributor(target, svc)
                Role.Standalone -> setUpStandalone(target)
                Role.None -> {}
            }
        }
    }

    private fun wireKensaCoreOnOutputOnlySourceSets(project: Project, extension: KensaExtension) {
        val outputOnly = extension.outputSourceSets.get() - extension.sourceSets.get()
        if (outputOnly.isEmpty()) return
        val sourceSetsContainer = project.extensions.findByType(SourceSetContainer::class.java) ?: return
        val kensaVer = extension.kensaCoreVersion.get()
        for (ssName in outputOnly) {
            val ss = sourceSetsContainer.findByName(ssName) ?: continue
            val dep = project.dependencies.create("dev.kensa:kensa-core:$kensaVer") as ModuleDependency
            dep.capabilities { caps ->
                caps.requireCapability("dev.kensa:core-hooks")
            }
            project.configurations.named(ss.implementationConfigurationName) { config ->
                config.dependencies.add(dep)
            }
        }
    }

    private fun setUpStandalone(project: Project) {
        val extension = project.kensaExtension
        if (!extension.site.get()) return

        val expectedSourceIds = extension.outputSourceSets.get().toMutableSet()
        val seenIds = mutableSetOf<String>()
        val configuredTestTasks = mutableListOf<Test>()

        for (sourceSetName in extension.outputSourceSets.get()) {
            val testTask = project.tasks.findByName(sourceSetName)
            if (testTask == null || testTask !is Test) continue

            val existingId = testTask.systemProperties["kensa.source.id"] as? String
            val resolvedId = existingId ?: sourceSetName
            if (!seenIds.add(resolvedId)) {
                throw GradleException(
                    "Kensa site mode: source id collision on '$resolvedId' (multiple sourcesets / test tasks resolve to the same kensa.source.id). Override one explicitly: tasks.named<Test>(\"<name>\") { systemProperty(\"kensa.source.id\", \"<unique>\") }"
                )
            }
            if (existingId != null && existingId != sourceSetName) {
                expectedSourceIds.remove(sourceSetName)
                expectedSourceIds.add(existingId)
            }

            val argsProvider = project.objects.newInstance(KensaSourceArgsProvider::class.java).apply {
                siteRoot.set(extension.siteRoot)
                sourceBundleDir.set(extension.siteRoot.dir("sources/$resolvedId"))
                sourceId.set(resolvedId)
                emitSourceIdArg.set(existingId == null)
            }
            testTask.jvmArgumentProviders.add(argsProvider)
            configuredTestTasks.add(testTask)
        }

        val shellConfig = project.configurations.maybeCreate("kensaShellResources").apply {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
            description = "Source jars for the Kensa multi-source site shell (kensa.js, logo.svg)."
            // kensa-core publishes two variants distinguished only by org.gradle.dependency.bundling
            // (external -> runtimeElements, shadowed -> shadowRuntimeElements). Pin to the plain,
            // non-shadowed jar so resolution is unambiguous; the shell resources live in it.
            attributes {
                it.attribute(
                    Bundling.BUNDLING_ATTRIBUTE,
                    project.objects.named(Bundling::class.java, Bundling.EXTERNAL),
                )
            }
        }
        val resolvedKensaCoreVersion = extension.kensaCoreVersion.get()
        project.dependencies.add(shellConfig.name, "dev.kensa:kensa-core:$resolvedKensaCoreVersion")

        val assembleTaskProvider = project.tasks.register(
            "assembleKensaSite",
            AssembleKensaSiteTask::class.java,
        ) { task ->
            task.group = "verification"
            task.description = "Assembles the Kensa multi-source site (shell + manifest) from per-sourceset bundles."
            task.siteRoot.set(extension.siteRoot)
            task.expectedSourceIds.set(expectedSourceIds)
            task.kensaVersion.set(resolvedKensaCoreVersion)
            task.sourceTitles.set(extension.sourceTitles)
            task.contributorVersions.set(emptyMap())
            task.shellSource.from(shellConfig)
            task.sourceConfigurations.from(
                project.fileTree(extension.siteRoot) {
                    it.include("sources/*/configuration.json")
                }
            )
            task.manifestJsonFile.set(extension.siteRoot.file("manifest.json"))
            task.indexHtmlFile.set(extension.siteRoot.file("index.html"))
            task.kensaJsFile.set(extension.siteRoot.file("kensa.js"))
            task.logoSvgFile.set(extension.siteRoot.file("logo.svg"))
            task.mustRunAfter(configuredTestTasks)
        }

        for (testTask in configuredTestTasks) {
            testTask.finalizedBy(assembleTaskProvider)
        }
    }

    private fun setUpContributor(project: Project, svc: KensaSiteService) {
        val agg = svc.aggregatorIntent() ?: return
        val extension = project.kensaExtension
        val slug = NamespacedId.slug(project.path, rootProjectName = agg.rootProjectName)
        val rootAssembleTaskPath = "${agg.projectPath.trimEnd(':')}:assembleKensaSite"

        for (sourceSetName in extension.outputSourceSets.get()) {
            val testTask = project.tasks.findByName(sourceSetName)
            if (testTask == null || testTask !is Test) continue

            val namespacedId = NamespacedId.format(slug, sourceSetName)
            val argsProvider = project.objects.newInstance(KensaSourceArgsProvider::class.java).apply {
                siteRoot.set(agg.siteRoot)
                sourceBundleDir.set(agg.siteRoot.resolve("sources").resolve(namespacedId))
                sourceId.set(namespacedId)
                emitSourceIdArg.set(true)
            }
            testTask.jvmArgumentProviders.add(argsProvider)
            testTask.finalizedBy(rootAssembleTaskPath)
        }

        project.logger.lifecycle(
            "Kensa: ${project.path} contributing to root :assembleKensaSite (siteRoot=${agg.siteRoot.absolutePath})"
        )
    }

    private fun setUpAggregator(
        project: Project,
        svc: KensaSiteService,
        siteService: Provider<KensaSiteService>,
    ) {
        val agg = svc.aggregatorIntent() ?: return
        val extension = project.kensaExtension
        val rootSlug = NamespacedId.slug(agg.projectPath, rootProjectName = agg.rootProjectName)

        val configuredOwnTestTasks = mutableListOf<Test>()
        for (sourceSetName in agg.outputSourceSets) {
            val testTask = project.tasks.findByName(sourceSetName)
            if (testTask == null || testTask !is Test) continue

            val namespacedId = NamespacedId.format(rootSlug, sourceSetName)
            val argsProvider = project.objects.newInstance(KensaSourceArgsProvider::class.java).apply {
                siteRoot.set(extension.siteRoot)
                sourceBundleDir.set(extension.siteRoot.dir("sources/$namespacedId"))
                sourceId.set(namespacedId)
                emitSourceIdArg.set(true)
            }
            testTask.jvmArgumentProviders.add(argsProvider)
            configuredOwnTestTasks.add(testTask)
        }

        val shellConfig = project.configurations.maybeCreate("kensaShellResources").apply {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
            description = "Source jars for the Kensa multi-source site shell (kensa.js, logo.svg)."
            // kensa-core publishes two variants distinguished only by org.gradle.dependency.bundling
            // (external -> runtimeElements, shadowed -> shadowRuntimeElements). Pin to the plain,
            // non-shadowed jar so resolution is unambiguous; the shell resources live in it.
            attributes {
                it.attribute(
                    Bundling.BUNDLING_ATTRIBUTE,
                    project.objects.named(Bundling::class.java, Bundling.EXTERNAL),
                )
            }
        }
        project.dependencies.add(shellConfig.name, "dev.kensa:kensa-core:${agg.kensaCoreVersion}")

        val registrations = svc.registrations()
        val expectedIds = registrations.map { it.namespacedId }.toSet()
        val aggregatorTitleMap = extension.sourceTitles.get()
        val effectiveTitles = buildMap {
            for (reg in registrations) {
                val title = aggregatorTitleMap[reg.namespacedId] ?: reg.localTitle
                if (title != null) put(reg.namespacedId, title)
            }
        }
        val contributorVersions = registrations
            .filter { it.projectPath != agg.projectPath }
            .associate { it.projectPath to it.kensaCoreVersion }

        val assembleTaskProvider = project.tasks.register(
            "assembleKensaSite",
            AssembleKensaSiteTask::class.java,
        ) { task ->
            task.group = "verification"
            task.description = "Assembles the aggregated Kensa multi-source site from per-module bundles."
            task.siteRoot.set(extension.siteRoot)
            task.expectedSourceIds.set(expectedIds)
            task.kensaVersion.set(agg.kensaCoreVersion)
            task.sourceTitles.set(effectiveTitles)
            task.contributorVersions.set(contributorVersions)
            task.shellSource.from(shellConfig)
            task.sourceConfigurations.from(
                project.fileTree(extension.siteRoot) {
                    it.include("sources/*/configuration.json")
                }
            )
            task.manifestJsonFile.set(extension.siteRoot.file("manifest.json"))
            task.indexHtmlFile.set(extension.siteRoot.file("index.html"))
            task.kensaJsFile.set(extension.siteRoot.file("kensa.js"))
            task.logoSvgFile.set(extension.siteRoot.file("logo.svg"))
            task.mustRunAfter(configuredOwnTestTasks)
            // Service contents are part of the task's input universe via configured state.
            task.usesService(siteService)
        }

        for (testTask in configuredOwnTestTasks) {
            testTask.finalizedBy(assembleTaskProvider)
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val extension = kotlinCompilation.project.kensaExtension

        if (!extension.enabled.get()) {
            return false
        }

        val compilationName = kotlinCompilation.name
        return extension.sourceSets.get().contains(compilationName)
    }

    override fun getCompilerPluginId(): String = "dev.kensa.compiler-plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "dev.kensa",
        artifactId = "kensa-compiler-plugin",
        version = capturedProject.kensaExtension.kensaCoreVersion.get()
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.project
        val extension = project.kensaExtension

        if (extension.enabled.get()) {
            val resolvedKensaCoreVersion = extension.kensaCoreVersion.get()
            kotlinCompilation.defaultSourceSet.dependencies {
                implementation("dev.kensa:kensa-core:$resolvedKensaCoreVersion") {
                    capabilities {
                        it.requireCapability("dev.kensa:core-hooks")
                    }
                }
            }
        }

        return project.provider {
            val options = mutableListOf<SubpluginOption>()

            if (extension.enabled.get()) {
                options.add(SubpluginOption("enabled", "true"))
            }

            if(extension.debug.get()) {
                options.add(SubpluginOption("debug", "true"))
            }
            options
        }
    }

    private fun checkKensaCoreCompat(requested: String) {
        if (GradleVersion.version(requested) < GradleVersion.version(MIN_KENSA_CORE_VERSION)) {
            throw GradleException(
                "dev.kensa.gradle-plugin (this is plugin $KENSA_VERSION) requires kensa-core >= $MIN_KENSA_CORE_VERSION " +
                        "but the project requested kensa-core $requested. " +
                        "Update or remove the `kensa { kensaCoreVersion.set(...) }` override."
            )
        }
    }

    private val Project.kensaExtension get() = extensions.getByType(KensaExtension::class.java)
}
