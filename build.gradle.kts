import org.gradle.api.JavaVersion.VERSION_17
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val ktorVersion = "3.0.0"

repositories {
  mavenCentral()
  maven("https://jitpack.io")
}
plugins {
  kotlin("jvm") version "2.0.20"
  kotlin("plugin.serialization") version "2.0.20"
}
dependencies {
  implementation("io.ktor:ktor-client-core:$ktorVersion")
  implementation("io.ktor:ktor-client-cio:$ktorVersion")
  implementation("io.ktor:ktor-client-resources:$ktorVersion")
  implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
  implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
  implementation("ch.qos.logback:logback-classic:1.4.5")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
  testImplementation("com.google.truth:truth:1.1.3")
  testImplementation("io.mockk:mockk:1.13.2")
}

java {
  sourceCompatibility = VERSION_17
  targetCompatibility = VERSION_17
}
kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_17
    freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
  }
}
tasks.jar {
  isZip64 = true
  manifest.attributes("Main-Class" to "AppKt")
  configurations["compileClasspath"].forEach { file: File ->
    from(zipTree(file.absoluteFile))
  }
  duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
