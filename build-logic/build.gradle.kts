plugins {
    `kotlin-dsl`
}

// build-logic compiles against the same toolchain the platforms do, read from the one catalog
// entry, so bumping `java` in libs.versions.toml moves the convention plugins and their consumers
// together instead of leaving the two a release apart.
java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

// The third-party plugins the `herobot.*` convention scripts apply on their consumers' behalf.
// They belong here rather than in the root build so a subproject picks them up by requesting a
// convention, and never by naming the plugin itself.
dependencies {
    implementation(libs.plugin.shadow)
    implementation(libs.plugin.run.paper)
    implementation(libs.plugin.yml.paper)

    // Makes the `libs` version catalog accessible from precompiled script plugins
    // via `the<LibrariesForLibs>()`.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
