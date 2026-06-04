package dev.kensa.gradle

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * Cross-module aggregation tests. Each subproject applies `dev.kensa.gradle-plugin` with
 * `kensa.site = true`; the root also applies the plugin with `kensa.site = true`. The plugin's
 * role discovery makes the root the Aggregator and the subprojects Contributors — bundles land
 * directly in `<rootDir>/build/kensa-site/sources/<slug>__<sourceSet>/`.
 */
class MultiProjectSiteModeFunctionalTest {

    private val kensaCoreVersion: String = System.getProperty("kensa.core.version")
        ?: error("System property 'kensa.core.version' not set; configure it on the functionalTest task.")

    @Test
    fun `aggregates sources from two subprojects into a single manifest with namespaced ids`(@TempDir projectDir: Path) {
        val repo = projectDir.resolve("test-repo")
        publishFakeKensaCore(repo)
        writeMultiProjectFixture(
            projectDir,
            repo,
            subprojects = listOf(
                Sub(":web", sourceSets = listOf("test")),
                Sub(":api", sourceSets = listOf("test")),
            ),
        )
        prePopulate(projectDir, "web__test", titleText = "Web Tests")
        prePopulate(projectDir, "api__test", titleText = "API Tests")

        val result = runner(projectDir).build()

        result.task(":assembleKensaSite")?.outcome shouldBe TaskOutcome.SUCCESS

        val siteRoot = projectDir.resolve("build/kensa-site")
        Files.exists(siteRoot.resolve("manifest.json")) shouldBe true
        Files.exists(siteRoot.resolve("index.html")) shouldBe true
        Files.exists(siteRoot.resolve("kensa.js")) shouldBe true
        Files.exists(siteRoot.resolve("logo.svg")) shouldBe true

        val manifestText = siteRoot.resolve("manifest.json").toFile().readText()
        manifestText shouldContain "\"id\": \"api__test\""
        manifestText shouldContain "\"title\": \"API Tests\""
        manifestText shouldContain "\"id\": \"web__test\""
        manifestText shouldContain "\"title\": \"Web Tests\""
    }

    @Test
    fun `aggregator sourceTitles override beats contributor's local title`(@TempDir projectDir: Path) {
        val repo = projectDir.resolve("test-repo")
        publishFakeKensaCore(repo)
        writeMultiProjectFixture(
            projectDir,
            repo,
            rootSourceTitles = mapOf("web__test" to "Root override"),
            subprojects = listOf(
                Sub(":web", sourceSets = listOf("test"), sourceTitles = mapOf("test" to "Contributor local")),
            ),
        )
        prePopulate(projectDir, "web__test", titleText = "Runtime default")

        runner(projectDir).build()

        val manifestText = projectDir.resolve("build/kensa-site/manifest.json").toFile().readText()
        manifestText shouldContain "\"title\": \"Root override\""
        manifestText shouldNotContain "\"title\": \"Contributor local\""
    }

    @Test
    fun `contributor's local sourceTitle is used when aggregator has no override`(@TempDir projectDir: Path) {
        val repo = projectDir.resolve("test-repo")
        publishFakeKensaCore(repo)
        writeMultiProjectFixture(
            projectDir,
            repo,
            subprojects = listOf(
                Sub(":web", sourceSets = listOf("test"), sourceTitles = mapOf("test" to "From subproject")),
            ),
        )
        prePopulate(projectDir, "web__test", titleText = "Runtime default")

        runner(projectDir).build()

        val manifestText = projectDir.resolve("build/kensa-site/manifest.json").toFile().readText()
        manifestText shouldContain "\"title\": \"From subproject\""
    }

    @Test
    fun `subproject with site=false does not contribute`(@TempDir projectDir: Path) {
        val repo = projectDir.resolve("test-repo")
        publishFakeKensaCore(repo)
        writeMultiProjectFixture(
            projectDir,
            repo,
            subprojects = listOf(
                Sub(":web", sourceSets = listOf("test")),
                Sub(":sandbox", sourceSets = listOf("test"), site = false),
            ),
        )
        prePopulate(projectDir, "web__test", titleText = "Web Tests")
        // Even if a stale sandbox__test directory exists on disk, it must be pruned because
        // :sandbox didn't register.
        prePopulate(projectDir, "sandbox__test", titleText = "Stale Sandbox")

        runner(projectDir).build()

        val manifestText = projectDir.resolve("build/kensa-site/manifest.json").toFile().readText()
        manifestText shouldContain "\"id\": \"web__test\""
        manifestText.shouldNotContainSourceId("sandbox__test")
        Files.exists(projectDir.resolve("build/kensa-site/sources/sandbox__test")) shouldBe false
    }

    @Test
    fun `contributor with different kensaCoreVersion fails aggregator task with actionable message`(@TempDir projectDir: Path) {
        val repo = projectDir.resolve("test-repo")
        publishFakeKensaCore(repo, version = kensaCoreVersion)
        publishFakeKensaCore(repo, version = "0.8.99")
        writeMultiProjectFixture(
            projectDir,
            repo,
            subprojects = listOf(
                Sub(":legacy", sourceSets = listOf("test"), kensaCoreVersionOverride = "0.8.99"),
            ),
        )
        prePopulate(projectDir, "legacy__test", titleText = "Legacy")

        val result = runner(projectDir).buildAndFail()

        result.output shouldContain "kensa-core version mismatch"
        result.output shouldContain ":legacy"
        result.output shouldContain "0.8.99"
    }

    @Test
    fun `aggregator's own source-sets are namespaced using root project name`(@TempDir projectDir: Path) {
        val repo = projectDir.resolve("test-repo")
        publishFakeKensaCore(repo)
        writeMultiProjectFixture(
            projectDir,
            repo,
            rootSourceSets = listOf("test"),
            subprojects = listOf(
                Sub(":web", sourceSets = listOf("test")),
            ),
        )
        // Root project name is "fixture" (set in writeMultiProjectFixture below).
        prePopulate(projectDir, "fixture__test", titleText = "Root Tests")
        prePopulate(projectDir, "web__test", titleText = "Web Tests")

        runner(projectDir).build()

        val manifestText = projectDir.resolve("build/kensa-site/manifest.json").toFile().readText()
        manifestText shouldContain "\"id\": \"fixture__test\""
        manifestText shouldContain "\"id\": \"web__test\""
    }

    @Test
    fun `subproject site=true with root site=false falls back to standalone per-subproject site`(@TempDir projectDir: Path) {
        val repo = projectDir.resolve("test-repo")
        publishFakeKensaCore(repo)
        writeMultiProjectFixture(
            projectDir,
            repo,
            rootSite = false,
            subprojects = listOf(
                Sub(":web", sourceSets = listOf("test")),
            ),
        )
        // Standalone (per-subproject) mode writes to web/build/kensa-site, NOT root/build/kensa-site.
        val webSiteRoot = projectDir.resolve("web/build/kensa-site")
        Files.createDirectories(webSiteRoot.resolve("sources/test"))
        Files.writeString(
            webSiteRoot.resolve("sources/test/configuration.json"),
            """{"titleText":"Standalone Web","kensaVersion":"$kensaCoreVersion"}"""
        )
        Files.writeString(webSiteRoot.resolve("sources/test/indices.json"), """{"indices":[]}""")
        Files.createDirectories(webSiteRoot.resolve("sources/test/results"))

        val result = runner(projectDir, ":web:assembleKensaSite").build()

        result.task(":web:assembleKensaSite")?.outcome shouldBe TaskOutcome.SUCCESS
        // Standalone mode keeps the bare source id (no namespacing).
        val manifestText = projectDir.resolve("web/build/kensa-site/manifest.json").toFile().readText()
        manifestText shouldContain "\"id\": \"test\""
        manifestText shouldContain "\"title\": \"Standalone Web\""
        // No aggregated site at the root.
        Files.exists(projectDir.resolve("build/kensa-site/manifest.json")) shouldBe false
    }

    @Test
    fun `contributor test tasks are finalized by the root aggregator's assembleKensaSite`(@TempDir projectDir: Path) {
        val repo = projectDir.resolve("test-repo")
        publishFakeKensaCore(repo)
        writeMultiProjectFixture(
            projectDir,
            repo,
            subprojects = listOf(
                Sub(":web", sourceSets = listOf("test")),
            ),
        )

        val result = runner(projectDir, "--dry-run", ":web:test").build()

        result.output shouldContain ":web:test"
        result.output shouldContain ":assembleKensaSite"
    }

    @Test
    fun `removing a subproject prunes its stale sources directory`(@TempDir projectDir: Path) {
        val repo = projectDir.resolve("test-repo")
        publishFakeKensaCore(repo)
        writeMultiProjectFixture(
            projectDir,
            repo,
            subprojects = listOf(
                Sub(":web", sourceSets = listOf("test")),
            ),
        )
        // Stale bundle from a previous run with a now-removed :billing.
        prePopulate(projectDir, "billing__test", titleText = "Removed")
        prePopulate(projectDir, "web__test", titleText = "Web Tests")

        runner(projectDir).build()

        Files.exists(projectDir.resolve("build/kensa-site/sources/billing__test")) shouldBe false
        Files.exists(projectDir.resolve("build/kensa-site/sources/web__test")) shouldBe true
    }

    private data class Sub(
        val path: String,
        val sourceSets: List<String>,
        val sourceTitles: Map<String, String> = emptyMap(),
        val site: Boolean = true,
        val kensaCoreVersionOverride: String? = null,
    )

    private fun writeMultiProjectFixture(
        projectDir: Path,
        repo: Path,
        subprojects: List<Sub>,
        rootSite: Boolean = true,
        rootSourceTitles: Map<String, String> = emptyMap(),
        rootSourceSets: List<String> = emptyList(),
        kotlinVersion: String = "2.4.0",
    ) {
        val include = subprojects.joinToString(", ") { "\"${it.path}\"" }
        projectDir.resolve("settings.gradle.kts").toFile().writeText(
            """
            rootProject.name = "fixture"
            include($include)
            """.trimIndent()
        )

        val rootTitlesBlock = rootSourceTitles.entries.joinToString("\n                ") { (k, v) ->
            "sourceTitles[\"$k\"] = \"$v\""
        }
        val rootSourceSetsBlock = if (rootSourceSets.isEmpty()) "" else
            "outputSourceSets = setOf(${rootSourceSets.joinToString { "\"$it\"" }})"
        // Root applies the plugin (acts as Aggregator) and registers shared repos for all
        // projects so the test-repo resolves consistently in subprojects too.
        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("dev.kensa.gradle-plugin")
                id("org.jetbrains.kotlin.jvm") version "$kotlinVersion" apply false
            }

            allprojects {
                repositories {
                    maven { url = uri("${repo.toUri()}") }
                }
            }

            kensa {
                site = $rootSite
                $rootSourceSetsBlock
                $rootTitlesBlock
            }
            ${if (rootSourceSets.contains("test")) "tasks.register<Test>(\"test\") { useJUnitPlatform() }" else ""}
            """.trimIndent()
        )

        for (sub in subprojects) {
            val subDir = projectDir.resolve(sub.path.trimStart(':').replace(':', '/'))
            Files.createDirectories(subDir)
            val overrideBlock = sub.kensaCoreVersionOverride?.let { "kensaCoreVersion.set(\"$it\")" } ?: ""
            val titlesBlock = sub.sourceTitles.entries.joinToString("\n                ") { (k, v) ->
                "sourceTitles[\"$k\"] = \"$v\""
            }
            val testTaskBlocks = sub.sourceSets.joinToString("\n") { ss ->
                if (ss == "test") "" else "tasks.register<Test>(\"$ss\") { useJUnitPlatform() }"
            }
            subDir.resolve("build.gradle.kts").toFile().writeText(
                """
                plugins {
                    id("dev.kensa.gradle-plugin")
                    id("org.jetbrains.kotlin.jvm") version "$kotlinVersion"
                }

                kensa {
                    site = ${sub.site}
                    outputSourceSets = setOf(${sub.sourceSets.joinToString { "\"$it\"" }})
                    $overrideBlock
                    $titlesBlock
                }

                $testTaskBlocks
                """.trimIndent()
            )
        }
    }

    private fun prePopulate(projectDir: Path, namespacedId: String, titleText: String) {
        val dir = projectDir.resolve("build/kensa-site/sources/$namespacedId")
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("configuration.json"),
            """{"titleText":"$titleText","kensaVersion":"$kensaCoreVersion"}"""
        )
        Files.writeString(dir.resolve("indices.json"), """{"indices":[]}""")
        Files.createDirectories(dir.resolve("results"))
    }

    private fun runner(projectDir: Path, vararg args: String): GradleRunner {
        val taskArgs = if (args.isEmpty()) listOf("assembleKensaSite") else args.toList()
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(taskArgs + listOf("--stacktrace"))
            .withPluginClasspath()
    }

    private fun String.shouldNotContainSourceId(id: String) {
        if (contains("\"id\": \"$id\"")) error("Expected manifest sources NOT to contain id='$id', but did:\n$this")
    }

    private fun publishFakeKensaCore(
        repoRoot: Path,
        kensaJsBytes: ByteArray = "// shell\n".toByteArray(),
        logoSvgBytes: ByteArray = "<svg/>".toByteArray(),
        version: String = kensaCoreVersion,
    ) {
        val artifactDir = repoRoot.resolve("dev/kensa/kensa-core/$version")
        Files.createDirectories(artifactDir)

        val jarPath = artifactDir.resolve("kensa-core-$version.jar")
        JarOutputStream(Files.newOutputStream(jarPath)).use { jos ->
            jos.putNextEntry(JarEntry("kensa.js"))
            jos.write(kensaJsBytes)
            jos.closeEntry()
            jos.putNextEntry(JarEntry("logo.svg"))
            jos.write(logoSvgBytes)
            jos.closeEntry()
        }

        Files.writeString(
            artifactDir.resolve("kensa-core-$version.pom"),
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>dev.kensa</groupId>
              <artifactId>kensa-core</artifactId>
              <version>$version</version>
              <packaging>jar</packaging>
            </project>
            """.trimIndent()
        )
    }
}
