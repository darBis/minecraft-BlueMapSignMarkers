plugins {
    `java-library`
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.kugge"
version = "0.0.2-SNAPSHOT"

val targetJavaVersion = 21
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

sourceSets {
    main {
        java {
            srcDir("src/ChatImage/src/main/java")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven ( "https://repo.bluecolored.de/releases" )
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("de.bluecolored:bluemap-api:2.7.4")
    implementation("com.mortennobel:java-image-scaling:0.8.6")
}

tasks.processResources {
    // IMPORTANT: do NOT call 'expand(...)' on the whole task.
    // Copy everything as-is by default.

    // If you need variable expansion, restrict it to text files only:
    filesMatching(listOf("plugin.yml", "**/*.yml", "**/*.yaml", "**/*.properties", "**/*.txt", "**/*.md")) {
        filteringCharset = "UTF-8"
        // adjust variables as needed
        expand(
            mapOf(
                "version" to project.version,
                "name" to project.name
            )
        )
    }
    // No filters on binaries (*.png, *.jpg, *.pdf, etc.)
}

tasks.compileJava {
    options.encoding = Charsets.UTF_8.name()
    options.release.set(21)
}

tasks.runServer {
    downloadPlugins {
        github("BlueMap-Minecraft", "BlueMap", "v5.11", "bluemap-5.11-paper.jar")
    }
    minecraftVersion("1.21.8")
    jvmArgs("-DPaper.IgnoreJavaVersion=true", "-Dcom.mojang.eula.agree=true")
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    // minimize() // Odkomentuj, pokud chceš zmenšit závislosti (může rozbít některé pluginy)
    // relocate("com.example.lib", "dev.kugge.libs.example")
}

tasks.build {
    dependsOn( tasks.shadowJar)
}