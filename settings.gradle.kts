rootProject.name = "silk-loader"

val isJitpackBuild = System.getenv("JITPACK") == "true"

include(":loader")

if (!isJitpackBuild) {
    //include(":testmod")
}