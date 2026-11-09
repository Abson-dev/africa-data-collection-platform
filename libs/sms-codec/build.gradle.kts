plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

group = "dev.abson.adc"
version = "0.1.0"

kotlin {
    // Java 17 bytecode: usable from the Bridge (JVM) and the Collect fork (Android 8+, AGP 8+).
    jvmToolchain(17)
}

// Pure JVM, no runtime dependencies, so it drops into Android unchanged.
dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("smsCodec") {
            artifactId = "sms-codec"
            from(components["java"])
        }
    }
}
