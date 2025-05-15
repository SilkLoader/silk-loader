plugins {
    id("maven-publish")
    id("java-library")
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
    api("com.google.guava:guava:${findProperty("guavaVersion")}")
    api("org.slf4j:slf4j-api:${findProperty("slf4jVersion")}")

    implementation("net.fabricmc:tiny-mappings-parser:${findProperty("tinyMappingsParserVersion")}")

    implementation("net.fabricmc:tiny-remapper:${findProperty("tinyRemapperVersion")}")
    implementation("net.fabricmc:access-widener:${findProperty("accessWidenerVersion")}")

    val asmVersion = findProperty("asmVersion") as String
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-analysis:$asmVersion")
    implementation("org.ow2.asm:asm-commons:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")

    compileOnly("org.jetbrains:annotations:${rootProject.findProperty("annotationsVersion")}")

    implementation("ch.qos.logback:logback-classic:${findProperty("logbackVersion")}")
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
    }
}