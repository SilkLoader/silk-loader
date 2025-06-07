plugins {
    id("de.rhm176.silk.silk-plugin") version "2.1.1"
}

silk {
  silkLoaderCoordinates = project(":loader")
}

dependencies {
    equilinox(silk.findEquilinoxGameJar())
}
