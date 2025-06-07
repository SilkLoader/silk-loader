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

    api("com.google.guava:guava:${property("guavaVersion")}")
    api("com.google.code.gson:gson:${property("gsonVersion")}")

    implementation("net.fabricmc:tiny-mappings-parser:${property("tinyMappingsParserVersion")}")

    implementation("net.fabricmc:tiny-remapper:${property("tinyRemapperVersion")}")
    implementation("net.fabricmc:access-widener:${property("accessWidenerVersion")}")

    val asmVersion = property("asmVersion") as String
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-analysis:$asmVersion")
    implementation("org.ow2.asm:asm-commons:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")

    testImplementation("uk.org.webcompere:system-stubs-core:${project.property("systemStubsVersion")}")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:${project.property("systemStubsVersion")}")
    testImplementation("com.google.jimfs:jimfs:${project.property("jimfsVersion")}")

    testImplementation(platform("org.junit:junit-bom:${project.property("junitVersion")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.mockito:mockito-core:${project.property("mockitoVersion")}")
    testImplementation("org.mockito:mockito-junit-jupiter:${project.property("mockitoVersion")}")
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
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

    repositories {
        val reposiliteBaseUrl = System.getenv("REPOSILITE_URL")
            ?: project.findProperty("reposiliteUrl") as String?

        if (!reposiliteBaseUrl.isNullOrBlank()) {
            maven {
                name = "Reposilite"

                val repoPath = if (project.version.toString().endsWith("-SNAPSHOT")) {
                    "/snapshots"
                } else {
                    "/releases"
                }
                url = uri("$reposiliteBaseUrl$repoPath")

                credentials(PasswordCredentials::class.java) {
                    username = System.getenv("REPOSILITE_USERNAME") ?: project.findProperty("reposiliteUsername") as String?
                    password = System.getenv("REPOSILITE_PASSWORD") ?: project.findProperty("reposilitePassword") as String?
                }

                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
    }
}