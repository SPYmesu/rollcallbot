plugins {
    java
    alias(libs.plugins.shadow)
}

group = "su.spyme"
version = "1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    implementation(libs.telegrambots.longpolling)
    implementation(libs.telegrambots.client)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.yaml)
    implementation(libs.slf4j.api)
    implementation(platform(libs.log4j.bom))
    implementation(libs.log4j.slf4j2)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.mockito.core)
    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

tasks.test {
    useJUnitPlatform()
    val agentPath = mockitoAgent.elements.map { it.single().asFile.absolutePath }
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${agentPath.get()}", "-Xshare:off") })
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveFileName = "RollcallBot.jar"
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "su.spyme.rollcallbot.Main"
    }
}
