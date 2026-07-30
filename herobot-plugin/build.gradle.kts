plugins {
    id("herobot.java")
    id("herobot.shade")
    id("herobot.paper-plugin")
    alias(libs.plugins.paperweight.userdev)
}

description = "HeroBot for Paper."

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.get())

    implementation(projects.herobotCommon)
}

paper {
    main = "hero.bane.herobot.paper.HeroBotPlugin"
}

tasks.shadowJar {
    archiveClassifier = ""
    archiveBaseName = "HeroBot"
}
