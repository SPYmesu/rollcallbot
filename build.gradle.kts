plugins {
    java
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
    maven("https://jitpack.io")
}

dependencies {
    implementation(libs.telegrambots.longpolling)
    implementation(libs.telegrambots.client)
    implementation(libs.simple.yaml)
    implementation(libs.slf4j.api)
    implementation(platform(libs.log4j.bom))
    implementation(libs.log4j.slf4j2)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName = "RollcallBot.jar"
    manifest {
        attributes["Main-Class"] = "su.spyme.rollcallbot.Main"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
}
