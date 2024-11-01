import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
  mavenCentral()
  maven("https://jitpack.io")
}
plugins {
  kotlin("jvm") version "2.0.20"
  id("com.github.johnrengelman.shadow") version "8.1.1"
}
dependencies {
  implementation("io.ktor:ktor-server-core-jvm:2.1.3")
  implementation("io.ktor:ktor-server-netty-jvm:2.1.3")
  implementation("ch.qos.logback:logback-classic:1.4.5")
  testImplementation("io.ktor:ktor-client-cio:2.1.3")
  testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
  testImplementation("com.google.truth:truth:1.1.3")
  testImplementation("io.mockk:mockk:1.13.2")
}
tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "17"
  kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.time.ExperimentalTime"
}
tasks.test {
  useJUnitPlatform()
}
tasks.jar {
  isZip64 = true
  manifest.attributes("Main-Class" to "AppKt")
}
tasks.shadowJar {
  minimize() // if build is unsuccessful, you can disable it
  // also, if build still unsuccessful, you can try to add mergeServiceFiles() call
}
tasks.build {
  dependsOn(tasks.shadowJar)
}
