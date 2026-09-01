import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow")
}

tasks.named<ShadowJar>("shadowJar") {
    relocate("de.bsommerfeld.pathetic", "hero.bane.herobot.libs.pathetic")

    dependencies {
        exclude(dependency("it.unimi.dsi:fastutil"))
    }

    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.kotlin_module")
    exclude("META-INF/maven/**")
}
