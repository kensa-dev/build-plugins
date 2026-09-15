package dev.kensa.site

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ShellResourcesTest {

    @Test
    fun `copies the embed script and favicon shipped beside kensa js`(@TempDir tempDir: Path) {
        val jar = jar(tempDir, "kensa.js", "logo.svg", "kensa-embed.js", "favicon.png")
        val siteRoot = Files.createDirectory(tempDir.resolve("site"))

        ShellResources.writeTo(siteRoot, listOf(jar))

        Files.readString(siteRoot.resolve("kensa-embed.js")) shouldBe "content of kensa-embed.js"
        Files.readString(siteRoot.resolve("favicon.png")) shouldBe "content of favicon.png"
    }

    @Test
    fun `assembles a core older than 0_9_5 that ships no embed script or favicon`(@TempDir tempDir: Path) {
        val jar = jar(tempDir, "kensa.js", "logo.svg")
        val siteRoot = Files.createDirectory(tempDir.resolve("site"))

        ShellResources.writeTo(siteRoot, listOf(jar))

        Files.readString(siteRoot.resolve("kensa.js")) shouldBe "content of kensa.js"
        Files.exists(siteRoot.resolve("kensa-embed.js")) shouldBe false
    }

    private fun jar(dir: Path, vararg names: String): Path {
        val path = dir.resolve("kensa-core.jar")
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            names.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("content of $name".toByteArray())
                zip.closeEntry()
            }
        }
        return path
    }
}
