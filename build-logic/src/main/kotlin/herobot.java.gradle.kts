import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `java-library`
}

val libs = the<LibrariesForLibs>()
val javaVersion = libs.versions.java.get().toInt()

repositories {
    mavenCentral()
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
        // Keep encoding pinned regardless of the platform default; without it some
        // special characters in sources are mangled.
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
