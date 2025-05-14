rootProject.name = "silk-loader"

val isJitpackBuild = System.getenv("JITPACK") == "true"

include(":loader")

if (!isJitpackBuild) {
    pluginManagement {
        resolutionStrategy {
            eachPlugin {
                requested.apply {
                    if ("$id" == "de.rhm176.silk") {
                        useModule("com.github.SilkLoader:silk-plugin:$version")
                    }
                }
            }
        }

        repositories {
            maven("https://jitpack.io")
            gradlePluginPortal()
        }
    }

    include(":testmod")
}