rootProject.name = "silk-loader"

val isJitpackBuild = System.getenv("JITPACK") == "true"
val isCIBuild = System.getenv("CI") == "true"

include(":loader")

if (!isJitpackBuild && !isCIBuild) {
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