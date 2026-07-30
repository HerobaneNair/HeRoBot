plugins {
    id("herobot.java")
    id("herobot.paper-plugin")
    alias(libs.plugins.paperweight.userdev)
}

description = "HeroBot for Paper."

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.get())

    implementation(projects.common)
}

paper {
    main = "hero.bane.herobot.paper.HeroBotPlugin"
}

tasks.shadowJar {
    archiveClassifier = ""
    archiveBaseName = "HeroBot"

    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.kotlin_module")
}
