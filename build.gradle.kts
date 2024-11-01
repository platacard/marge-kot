import org.gradle.api.JavaVersion.VERSION_17
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

repositories {
  mavenCentral()
  maven("https://jitpack.io")
}
plugins {
  kotlin("jvm") version "2.0.20"
}
dependencies {
  implementation("io.ktor:ktor-server-core-jvm:2.1.3")
  implementation("io.ktor:ktor-server-netty-jvm:2.1.3")
  implementation("ch.qos.logback:logback-classic:1.4.5")
  testImplementation("io.ktor:ktor-client-cio:2.1.3")
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
tasks.test {
  useJUnitPlatform()
}
tasks.jar {
  isZip64 = true
  manifest.attributes("Main-Class" to "AppKt")
}
