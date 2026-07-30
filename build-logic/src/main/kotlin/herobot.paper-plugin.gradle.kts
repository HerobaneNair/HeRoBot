import org.gradle.accessors.dm.LibrariesForLibs
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("de.eldoria.plugin-yml.paper")
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

val libs = the<LibrariesForLibs>()
val toolchains = extensions.getByType<JavaToolchainService>()
val minecraftVersion = libs.versions.minecraft.get()

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
}

// Generates paper-plugin.yml. Modules applying this plugin still have to set
// `paper { main = ... }` themselves.
paper {
    name = "HeroBot"
    version = project.version.toString()
    description = rootProject.description

    apiVersion = minecraftVersion
    authors = listOf("HerobaneNair")
}

tasks {
    named<Jar>("jar") {
        archiveClassifier = "thin"
    }

    named<RunServer>("runServer") {
        group = "herobot"
        description = "Runs a Paper $minecraftVersion dev server with HeroBot installed."

        minecraftVersion(minecraftVersion)
        runDirectory = layout.projectDirectory.dir("run")
        javaLauncher = toolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
        }

        jvmArgs("-Dcom.mojang.eula.agree=true")
        pluginJars(named<Jar>("shadowJar").flatMap { it.archiveFile })
    }
}
