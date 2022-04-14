plugins {
  kotlin("multiplatform") version "1.6.20"
}

kotlin {
  jvm()
  js(IR) { browser() }
}