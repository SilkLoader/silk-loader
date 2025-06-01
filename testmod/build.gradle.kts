plugins {
    id("de.rhm176.silk") version "1.4.0"
}

silk {
  silkLoaderCoordinates = project(":loader")
}

dependencies {
    equilinox(silk.findEquilinoxGameJar())
}
