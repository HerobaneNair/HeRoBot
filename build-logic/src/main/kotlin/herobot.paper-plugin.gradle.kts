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

    apiVersion = libs.versions.compat.api.get()
    authors = listOf("HerobaneNair")
}

// Shared setup for every dev server flavour: same toolchain, same HeroBot jar,
// same companion plugins. Each flavour still picks its own run directory so the
// worlds do not clash.
fun RunServer.configureHeroBotRun(flavour: String, directory: String) {
    group = "herobot"
    description = "Runs a $flavour $minecraftVersion dev server with HeroBot installed."

    minecraftVersion(minecraftVersion)
    runDirectory = layout.projectDirectory.dir(directory)
    javaLauncher = toolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
    }

    jvmArgs("-Dcom.mojang.eula.agree=true")
    pluginJars(tasks.named<Jar>("shadowJar").flatMap { it.archiveFile })

    downloadPlugins {
        modrinth("simple-voice-chat", "bukkit-${libs.versions.voicechat.mod.get()}")
    }
}

tasks {
    named<Jar>("jar") {
        archiveClassifier = "thin"
    }

    named<RunServer>("runServer") {
        configureHeroBotRun("Paper", "run")
    }
}

runPaper.folia.registerTask {
    configureHeroBotRun("Folia", "run-folia")
}
