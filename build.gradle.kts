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
}

tasks.test {
    useJUnitPlatform()
}