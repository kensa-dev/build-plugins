package dev.kensa.gradle.site

object NamespacedId {

    const val SEPARATOR = "__"

    fun slug(projectPath: String, rootProjectName: String? = null): String {
        if (projectPath == ":") {
            return requireNotNull(rootProjectName) {
                "rootProjectName must be supplied when slugifying the root project path \":\""
            }
        }
        return projectPath.removePrefix(":").replace(':', '-')
    }

    fun format(slug: String, sourceSetName: String): String {
        require(!slug.contains(SEPARATOR)) {
            "Slug '$slug' contains the reserved namespacing separator '$SEPARATOR'."
        }
        require(!sourceSetName.contains(SEPARATOR)) {
            "Source set name '$sourceSetName' contains the reserved namespacing separator '$SEPARATOR'."
        }
        return "$slug$SEPARATOR$sourceSetName"
    }
}
