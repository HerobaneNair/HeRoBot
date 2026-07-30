plugins {
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.paperweight.userdev) apply false
}

description = "Robots scripted to be Heroes"

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
