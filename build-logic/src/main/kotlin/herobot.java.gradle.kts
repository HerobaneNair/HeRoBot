import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `java-library`
}

val libs = the<LibrariesForLibs>()
val javaVersion = libs.versions.java.get().toInt()

// <mc version>-<project version>+v<yyMMdd>, stamped once by the root build so the Fabric and Paper
// artifacts of one build always agree. Read it back as `artifactVersion` from either module.
extra["artifactVersion"] = rootProject.extra["artifactVersion"] as String

repositories {
    mavenCentral()
    maven("https://maven.maxhenkel.de/repository/public") { name = "Henkelmax" }
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    testCompileOnly(libs.jetbrains.annotations)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = javaVersion
    }

    withType<Test>().configureEach {
        useJUnitPlatform()
        failOnNoDiscoveredTests = false
    }

    withType<Jar>().configureEach {
        isReproducibleFileOrder = true
        isPreserveFileTimestamps = false
    }
}
