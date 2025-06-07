rootProject.name = "silk-loader"

val isJitpackBuild = System.getenv("JITPACK") == "true"
val isCIBuild = System.getenv("CI") == "true"

include(":loader")

if (!isJitpackBuild && !isCIBuild) {
    pluginManagement {
        repositories {
            maven {
                url = uri("https://maven.rhm176.de/releases")
                name = "RHM's Maven"
            }
            maven("https://jitpack.io")
            gradlePluginPortal()
        }
    }

    include(":testmod")
}
