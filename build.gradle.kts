import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.paperweight.userdev) apply false
}

description = "Robots scripted to be Heroes"

val buildTime: LocalDateTime = LocalDateTime.now()
val minecraftVersion = libs.versions.minecraft.get()

val artifactVersion = "$minecraftVersion-$version+v${buildTime.format(DateTimeFormatter.ofPattern("yyMMdd"))}"
val generation = "$minecraftVersion-$version+v${buildTime.format(DateTimeFormatter.ofPattern("yyMMdd_HHmm"))}"

extra["artifactVersion"] = artifactVersion

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

tasks.register<Sync>("buildAll") {
    group = "herobot"
    description = "Builds the Fabric mod and the Paper plugin into a build/libs generation folder."

    from(project(":herobot-mod").tasks.named("shadowJar"))
    from(project(":herobot-plugin").tasks.named("shadowJar"))
    into(layout.buildDirectory.dir("libs/$generation"))

    doLast {
        logger.lifecycle("Built into ${destinationDir}:")
        destinationDir.listFiles()?.sorted()?.forEach { logger.lifecycle("  ${it.name}") }
    }
}
