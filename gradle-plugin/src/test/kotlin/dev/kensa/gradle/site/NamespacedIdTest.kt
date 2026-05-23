package dev.kensa.gradle.site

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class NamespacedIdTest {

    @Test
    fun `slug for root project drops the leading colon`() {
        NamespacedId.slug(":web") shouldBe "web"
    }

    @Test
    fun `slug for nested project replaces inner colons with hyphens`() {
        NamespacedId.slug(":libs:billing") shouldBe "libs-billing"
        NamespacedId.slug(":libs:auth:tokens") shouldBe "libs-auth-tokens"
    }

    @Test
    fun `slug for the root project itself uses its name`() {
        NamespacedId.slug(":", rootProjectName = "myapp") shouldBe "myapp"
    }

    @Test
    fun `slug preserves hyphens already present in project names`() {
        NamespacedId.slug(":my-web") shouldBe "my-web"
        NamespacedId.slug(":libs:my-billing") shouldBe "libs-my-billing"
    }

    @Test
    fun `format combines slug and source set name with double underscore`() {
        NamespacedId.format(slug = "web", sourceSetName = "test") shouldBe "web__test"
        NamespacedId.format(slug = "libs-billing", sourceSetName = "uiTest") shouldBe "libs-billing__uiTest"
    }

    @Test
    fun `format rejects source set names containing the reserved separator`() {
        val ex = shouldThrow<IllegalArgumentException> {
            NamespacedId.format(slug = "web", sourceSetName = "weird__name")
        }
        ex.message!! shouldContain "__"
    }

    @Test
    fun `format rejects slugs containing the reserved separator`() {
        // Defensive: a project path that decoded to a slug with __ would conflict with the separator.
        val ex = shouldThrow<IllegalArgumentException> {
            NamespacedId.format(slug = "weird__slug", sourceSetName = "test")
        }
        ex.message!! shouldContain "__"
    }
}
