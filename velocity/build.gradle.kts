import org.gradle.api.tasks.SourceSetContainer

plugins {
    `java-library`
    kotlin("jvm")
    id("com.gradleup.shadow")
}

evaluationDependsOn(":common")
val common = project(":common")
val commonSources = common.extensions.getByType<SourceSetContainer>()
val artifactName = "CrabKotlin"
base.archivesName.set(artifactName)

dependencies {
    api(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:4.2.1-SNAPSHOT")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching(listOf("plugin.yml", "velocity-plugin.json")) {
        expand("version" to project.version, "description" to project.description!!,
            "website" to "https://github.com/xiaozhangup/CrabKotlin")
    }
}

tasks.jar { archiveClassifier.set("plain-velocity") }
tasks.shadowJar {
    archiveClassifier.set("velocity")
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    mergeServiceFiles()
    dependencies { exclude { it.moduleName.contains("annotations") } }
}

// Includes the shared API and this platform only, without Kotlin/runtime libraries.
val apiJar = tasks.register<Jar>("apiJar") {
    archiveClassifier.set("api-velocity")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output) {
        include("me/xiaozhangup/crab/**", "META-INF/*.kotlin_module")
    }
    from(commonSources.named("main").map { it.output })
    from(common.configurations.named("databaseApi").map { config -> config.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/MANIFEST.MF")
    }
}

tasks.assemble { dependsOn(tasks.shadowJar, apiJar) }
