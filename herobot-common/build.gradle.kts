plugins {
    id("herobot.java")
}

description = "Platform independent HeroBot code. Shaded into both the Fabric mod and the Paper plugin."

dependencies {
    // `api` so the platform modules compile against pathetic through this module, and so both
    // shadowJars pick it up transitively. fastutil arrives with the engine but is provided by
    // Minecraft and Paper alike, so `herobot.shade` drops it from the bundled output.
    api(libs.pathetic.api)
    api(libs.pathetic.engine)
}
