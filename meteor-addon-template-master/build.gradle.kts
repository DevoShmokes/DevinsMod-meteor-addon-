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
    maven {
        name = "modrinth"
        url = uri("https://api.modrinth.com/maven")
        content { includeGroup("maven.modrinth") }
    }
    maven {
        name = "rusherhack"
        url = uri("https://maven.rusherhack.org/snapshots")
    }
    maven { url = uri("https://maven.kosmx.dev/") }
    maven { url = uri("https://www.cursemaven.com") }
    maven { url = uri("https://masa.dy.fi/maven") }
    maven { url = uri("https://maven.meteordev.org/releases") }
    maven { url = uri("https://maven.meteordev.org/snapshots") }
    maven { url = uri("https://maven.maxhenkel.de/releases/") }
}

dependencies {
    // Minecraft & Fabric
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    // Libraries
    implementation("de.bommel24.nvmlj:nvmlj:1.0.2")
    include("de.bommel24.nvmlj:nvmlj:1.0.2")

    implementation("com.github.hypfvieh:dbus-java-core:5.1.1")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.1")
    include("com.github.hypfvieh:dbus-java-core:5.1.1")
    include("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.1")

    modImplementation("com.googlecode.json-simple:json-simple:1.1.1")

    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    implementation("org.codehaus.janino:janino:3.1.12")

    // Meteor Client & Baritone (compile-only)
    add("modCompileOnly", "meteordevelopment:meteor-client:$meteorVersion")
    add("modCompileOnly", "meteordevelopment:baritone:${baritoneVersion}-SNAPSHOT")

    // Optional mods
    modCompileOnly("maven.modrinth:xaeroplus:2.26.5+fabric-1.21.4")
    modCompileOnly("maven.modrinth:emotecraft:2.5.5+1.21.4-fabric")
    modCompileOnly("dev.kosmx.player-anim:player-animation-lib-fabric:2.0.2+1.21.3")
    modCompileOnly("maven.modrinth:xaeros-minimap:25.2.0_Fabric_1.21.5")
    modCompileOnly("maven.modrinth:xaeros-world-map:1.39.4_Fabric_1.21.4")
    modCompileOnly("maven.modrinth:litematica:0.21.2")
    modCompileOnly("maven.modrinth:malilib:0.23.2")
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
