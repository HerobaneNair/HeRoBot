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
