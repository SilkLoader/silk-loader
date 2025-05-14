plugins {
    id("maven-publish")
    id("java-library")
    id("java")
}

allprojects {
    apply(plugin = "java")

    java {
        val javaLanguageVersion = JavaLanguageVersion.of(rootProject.findProperty("javaVersion").toString())
        val javaVersion = JavaVersion.toVersion(javaLanguageVersion.asInt())

        toolchain {
            languageVersion = javaLanguageVersion
        }

        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
}