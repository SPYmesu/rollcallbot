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

val telegramBotsVersion = "10.3.0"
val log4jVersion = "2.26.1"
val lombokVersion = "1.18.48"

dependencies {
    implementation("org.telegram:telegrambots-longpolling:$telegramBotsVersion")
    implementation("org.telegram:telegrambots-client:$telegramBotsVersion")
    implementation("me.carleslc.Simple-YAML:Simple-Yaml:1.8.4")
    implementation("org.slf4j:slf4j-api:2.0.19")
    implementation(platform("org.apache.logging.log4j:log4j-bom:$log4jVersion"))
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl")
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
