plugins {
    `kotlin-dsl`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.plugin.shadow)
    implementation(libs.plugin.run.paper)
    implementation(libs.plugin.yml.paper)

    // Makes the `libs` version catalog accessible from precompiled script plugins
    // via `the<LibrariesForLibs>()`.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
