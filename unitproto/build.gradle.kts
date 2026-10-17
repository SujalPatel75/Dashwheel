// What Dashwheel knows about head unit firmwares, free of Android: which
// family a unit belongs to (UnitDetector), and the wire formats and code
// tables of each family's vendor interfaces. Plain Kotlin, so it is unit-tested
// on the JVM and shared with the fake-firmware test app (:fakeunit).
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
