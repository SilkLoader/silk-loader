plugins {
    id("maven-publish")
    id("java-library")
    id("com.gradleup.shadow") version "9.0.0-beta13"
}

version = findProperty("version")!!
group = "de.rhm176.silk"

repositories {
    mavenCentral()

    maven {
        url = uri("https://maven.fabricmc.net/")
        name = "FabricMC"
    }
}

dependencies {
    api("net.fabricmc:fabric-loader:${findProperty("fabricLoaderVersion")}")
    api("net.fabricmc:sponge-mixin:${findProperty("mixinVersion")}") {
        exclude(group = "com.google.code.gson")
        exclude(group = "com.google.guava")
    }

    implementation("com.google.guava:guava:${findProperty("guavaVersion")}")

    implementation("net.fabricmc:tiny-mappings-parser:${findProperty("tinyMappingsParserVersion")}")

    implementation("net.fabricmc:tiny-remapper:${findProperty("tinyRemapperVersion")}")
    implementation("net.fabricmc:access-widener:${findProperty("accessWidenerVersion")}")

    val asmVersion = findProperty("asmVersion") as String
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-analysis:$asmVersion")
    implementation("org.ow2.asm:asm-commons:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            artifactId = "silk-loader"
        }

        create<MavenPublication>("mavenShadow") {
            from(components["shadow"])

            artifactId = "silk-loader"
        }
    }
}

tasks.shadowJar {
    archiveClassifier.set("fat")

    mergeServiceFiles()

    manifest {
        attributes(mapOf(
            "Main-Class" to "de.rhm176.loader.Main",
        ))
    }

    dependencies {
        exclude(dependency("net.fabricmc:fabric-loader:.*"))
    }
}