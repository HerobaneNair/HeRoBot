import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.fabric.loom)
    id("herobot.java")
    `maven-publish`
}

description = "HeroBot for Fabric."

version = "${libs.versions.minecraft.get()}-${rootProject.version}+v${LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))}"

base {
    archivesName = "herobot"
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("herobot") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

repositories {
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
}

// Classes merged straight into the mod jar, so `common` needs no mod metadata and
// stays in the mod's own classloader.
val shade = configurations.dependencyScope("shade")
val shadeClasspath = configurations.resolvable("shadeClasspath") {
    extendsFrom(shade.get())
    isTransitive = false
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)

    modImplementation(libs.fabric.api)

    modCompileOnly(libs.carpet)

    implementation(projects.common)
    add(shade.name, projects.common)
}

tasks {
    processResources {
        val props = mapOf(
            "version" to project.version,
            "minecraft_version" to libs.versions.minecraft.get(),
            "loader_version" to libs.versions.fabric.loader.get(),
        )

        inputs.properties(props)
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(props)
        }
    }

    jar {
        dependsOn(shadeClasspath)
        from({ shadeClasspath.get().map(::zipTree) }) {
            exclude("META-INF/**")
        }

        from(rootProject.file("LICENSE.txt")) {
            rename { "${it}_${base.archivesName.get()}" }
        }
    }
}

java {
    // Loom attaches sourcesJar to a RemapSourcesJar task and to `build` when present.
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }
}
