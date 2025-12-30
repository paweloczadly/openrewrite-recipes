plugins {
    idea
    java

    id("com.vanniktech.maven.publish") version libs.versions.maven.publish.get()

    // The plugin automatically configures the project with meaningful conventions like necessary compiler options.
    alias(libs.plugins.openrewrite.recipe.library.base)

    // Configures artifact repositories used for dependency resolution to include maven central and nexus snapshots.
    alias(libs.plugins.openrewrite.recipe.repositories)
}

group = "io.oczadly"
version = System.getenv("RELEASE_VERSION") ?: "0.0.1-SNAPSHOT"

dependencies {
    // The bill of materials (BOM) will manage the versions of any rewrite dependencies that are included within the project.
    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:${libs.versions.openrewrite.bom.get()}"))

    implementation("org.openrewrite:rewrite-hcl")

    // lombok is optional, but recommended from the documentation for authoring recipes
    compileOnly("org.projectlombok:lombok:${libs.versions.lombok.get()}")
    annotationProcessor("org.projectlombok:lombok:${libs.versions.lombok.get()}")

    // For authoring tests for any kind of Recipe
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation(libs.bundles.junit)
    testImplementation("org.assertj:assertj-core:${libs.versions.assertj.get()}")
}

mavenPublishing {
    coordinates(group.toString(), name.toString(), version.toString())

    pom {
        name.set(name.toString())
        description.set("OpenRewrite recipes for automated refactoring.")
        url.set("https://github.com/paweloczadly/openrewrite-recipes")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("paweloczadly")
                name.set("Paweł Oczadły")
                url.set("https://oczadly.io")
            }
        }

        scm {
            url.set("https://github.com/paweloczadly/openrewrite-recipes")
            connection.set("scm:git:https://github.com/paweloczadly/openrewrite-recipes.git")
            developerConnection.set("scm:git:ssh://git@github.com/paweloczadly/openrewrite-recipes.git")
        }
    }
}
