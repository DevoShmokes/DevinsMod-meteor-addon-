plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("com.gradleup.shadow") version "8.3.5"
}

base {
    archivesName.set(properties["archives_base_name"] as String)
    version = properties["mod_version"] as String
    // 'group' removed to use default project.group from settings
}

// Pull version properties
val meteorVersion: String = project.property("meteor_version") as String
val baritoneVersion: String = project.property("baritone_version") as String

configurations {
    create("rusherhackApi") {
        isCanBeResolved = true
    }
    compileOnly {
        extendsFrom(configurations["rusherhackApi"])
    }
    create("productionRuntime") {
        extendsFrom(
            configurations["minecraftLibraries"],
            configurations["loaderLibraries"],
            configurations["minecraftRuntimeLibraries"]
        )
    }
}

repositories {
    mavenCentral()

    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://api.modrinth.com/maven") }

    maven { url = uri("https://maven.rusherhack.org/snapshots") }

    // Meteor Client releases & snapshots
    maven {
        name = "Meteor Dev Releases"
        url  = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "Meteor Dev Snapshots"
        url  = uri("https://maven.meteordev.org/snapshots")
    }

    maven {
        name = "babbaj-repo"
        url = uri("https://babbaj.github.io/maven/")
    }

}


dependencies {
    // Minecraft & Fabric
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    modCompileOnly("meteordevelopment:meteor-client:$meteorVersion")
    modRuntimeOnly("meteordevelopment:meteor-client:$meteorVersion")

    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    modImplementation("com.googlecode.json-simple:json-simple:1.1.1")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    implementation("org.codehaus.janino:janino:3.1.12")

    implementation("dev.babbaj:nether-pathfinder:1.6")
    // Meteor Client & Baritone (compile-only)
    modCompileOnly("meteordevelopment:baritone:${baritoneVersion}-SNAPSHOT")
    modRuntimeOnly("meteordevelopment:baritone:${baritoneVersion}-SNAPSHOT")

}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
