plugins {
    id("de.rhm176.silk") version "v1.0.3"
}

dependencies {
    equilinox(files(silk.findEquilinoxGameJar()))

    implementation(project(":loader"))
}