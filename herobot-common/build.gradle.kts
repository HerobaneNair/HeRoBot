plugins {
    id("herobot.java")
}

description = "Platform independent HeroBot code. Shaded into both the Fabric mod and the Paper plugin."

dependencies {
    // Minecraft and Paper both ship slf4j, so it is compiled against but never bundled.
    compileOnly(libs.slf4j.api)

    // Gson ships with both Minecraft and Paper, so it is compiled against but never bundled.
    compileOnly(libs.gson)

    compileOnly(libs.voicechat.api)

    // `api` so the platform modules compile against pathetic through this module, and so both
    // shadowJars pick it up transitively. fastutil arrives with the engine but is provided by
    // Minecraft and Paper alike, so `herobot.shade` drops it from the bundled output.
    api(libs.pathetic.api)
    api(libs.pathetic.engine)
}
