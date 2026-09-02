@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.fabric.loom)
    id("herobot.java")
    id("herobot.shade")
    `maven-publish`
}

description = "HeroBot for Fabric."

version = extra["artifactVersion"] as String

base {
    archivesName = "herobot-fabric"
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

// What gets bundled into the mod jar: `common` plus everything it exposes as `api`
// (pathetic). Transitive, unlike the rest of the runtime classpath, which is Minecraft
// and the mod dependencies and must stay out of the jar.
val shade = configurations.dependencyScope("shade")
val shadeClasspath = configurations.resolvable("shadeClasspath") {
    extendsFrom(shade.get())
}

val voicechatMod = configurations.dependencyScope("voicechatMod")
val voicechatModClasspath = configurations.resolvable("voicechatModClasspath") {
    extendsFrom(voicechatMod.get())
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)

    implementation(libs.fabric.api)

    compileOnly(libs.carpet)

    compileOnly(libs.voicechat.api)
    "clientCompileOnly"(libs.voicechat.api)

    implementation(projects.herobotCommon)
    add(shade.name, projects.herobotCommon)

    add(voicechatMod.name, "maven.modrinth:simple-voice-chat:fabric-${libs.versions.voicechat.mod.get()}+${libs.versions.minecraft.get()}") {
        isTransitive = false
    }
}

val installRunMods = tasks.register<Sync>("installRunMods") {
    group = "herobot"
    description = "Installs the mods the dev runs need alongside HeroBot into run/mods."

    from(voicechatModClasspath)
    into(layout.projectDirectory.dir("run/mods"))
}

val acceptServerEula = tasks.register("acceptServerEula") {
    group = "herobot"
    description = "Agrees to the Minecraft EULA for the dev server, as the Paper run does inline."

    val eula = layout.projectDirectory.file("run/eula.txt")
    outputs.file(eula)

    doLast {
        eula.asFile.parentFile.mkdirs()
        eula.asFile.writeText("eula=true" + System.lineSeparator())
    }
}

tasks {
    named("runClient") { dependsOn(installRunMods) }
    named("runServer") { dependsOn(installRunMods, acceptServerEula) }

    processResources {
        val props = mapOf(
            "version" to project.version,
            "minecraft_version" to libs.versions.minecraft.get(),
            "minecraft_range" to libs.versions.compat.range.get(),
            "loader_version" to libs.versions.fabric.loader.get(),
        )

        inputs.properties(props)
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(props)
        }
    }

    jar {
        archiveClassifier = "thin"
        destinationDirectory = layout.buildDirectory.dir("devlibs")
    }

    shadowJar {
        archiveClassifier = ""

        configurations = listOf(shadeClasspath.get())

        // Loom adds the client source set to `jar`, not to shadowJar.
        from(sourceSets["client"].output)

        from(rootProject.file("LICENSE.txt")) {
            rename { "${it}_${base.archivesName.get()}" }
        }
    }
}

java {
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
