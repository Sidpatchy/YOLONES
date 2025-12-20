plugins {
    id("java")
}

group = "com.sidpatchy.yolones"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.apache.logging.log4j:log4j-api:2.25.2")
    implementation("org.apache.logging.log4j:log4j-core:2.25.2")
    implementation("net.java.jinput:jinput:2.0.10")
    val jinputNatives = configurations.create("jinputNatives")
    dependencies {
        jinputNatives("net.java.jinput:jinput:2.0.10:natives-all")
    }
}

val extractNatives = tasks.register<Copy>("extractNatives") {
    from(configurations.getByName("jinputNatives").map { zipTree(it) })
    into(layout.buildDirectory.dir("natives/jinput"))
}

tasks.test {
    dependsOn(extractNatives)
    systemProperty("java.library.path", layout.buildDirectory.dir("natives/jinput").get().asFile.absolutePath)
    useJUnitPlatform()
}

tasks.withType<JavaExec> {
    dependsOn(extractNatives)
    systemProperty("java.library.path", layout.buildDirectory.dir("natives/jinput").get().asFile.absolutePath)
}