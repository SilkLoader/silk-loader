plugins {
    id("maven-publish")
    id("java-library")
}

version = property("version")!!
group = "de.rhm176.silk"

repositories {
    mavenCentral()

    maven {
        url = uri("https://maven.fabricmc.net/")
        name = "FabricMC"
    }
}

dependencies {
    api("net.fabricmc:fabric-loader:${property("fabricLoaderVersion")}")
    api("net.fabricmc:sponge-mixin:${property("mixinVersion")}") {
        exclude(group = "com.google.code.gson")
        exclude(group = "com.google.guava")
    }
    api("io.github.llamalad7:mixinextras-fabric:${property("mixinExtrasVersion")}")

    implementation("com.google.guava:guava:${property("guavaVersion")}")
    implementation("net.fabricmc:tiny-mappings-parser:${property("tinyMappingsParserVersion")}")

    implementation("net.fabricmc:tiny-remapper:${property("tinyRemapperVersion")}")
    implementation("net.fabricmc:access-widener:${property("accessWidenerVersion")}")

    val asmVersion = property("asmVersion") as String
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
tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "de.rhm176.silk.loader.Main",
            "Implementation-Version" to project.version
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            artifactId = "silk-loader"
        }
    }
}