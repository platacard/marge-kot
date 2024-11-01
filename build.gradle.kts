import org.gradle.api.JavaVersion.VERSION_21
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21

repositories {
  mavenCentral()
  maven("https://jitpack.io")
  maven("https://repo.spring.io/milestone")
  maven("https://repo.spring.io/snapshot")
}
plugins {
  id("org.springframework.boot") version "3.4.0-SNAPSHOT"
  id("io.spring.dependency-management") version "1.1.6"
  kotlin("jvm") version "2.0.20"
  kotlin("plugin.spring") version "2.0.20"
}
dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("com.google.truth:truth:1.4.4")
  testImplementation("io.mockk:mockk:1.13.12")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java {
  sourceCompatibility = VERSION_21
  targetCompatibility = VERSION_21
}
kotlin {
  compilerOptions {
    jvmTarget = JVM_21
    freeCompilerArgs.addAll(
      "-Xjsr305=strict",
      "-Xvalue-classes",
      "-opt-in=kotlin.ExperimentalStdlibApi",
      "-opt-in=kotlin.time.ExperimentalTime"
    )
  }
}
tasks.test {
  useJUnitPlatform()
  jvmArgs("--enable-preview")
}
tasks.bootJar {
  archiveVersion.set("boot")
}
