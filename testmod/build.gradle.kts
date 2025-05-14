plugins {
    id("de.rhm176.silk")
}

dependencies {
    equilinox(files("C:\\Users\\RHM\\scoop\\apps\\steam\\current\\steamapps\\common\\Equilinox\\EquilinoxWindows.jar"))

    implementation(project(":loader"))
}