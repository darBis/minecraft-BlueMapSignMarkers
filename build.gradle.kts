plugins {
    `java-library`
    id("xyz.jpenilla.run-paper") version "2.3.1"
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
}

tasks.compileJava {
    options.encoding = Charsets.UTF_8.name()
    options.release.set(21)
}

tasks.processResources {
    filter { line -> line.replace("\${version}", project.version.toString()) }
}

tasks.runServer {
    downloadPlugins {
        github("BlueMap-Minecraft", "BlueMap", "v5.11", "bluemap-5.11-paper.jar")
    }
    minecraftVersion("1.21.8")
    jvmArgs("-DPaper.IgnoreJavaVersion=true", "-Dcom.mojang.eula.agree=true")
}