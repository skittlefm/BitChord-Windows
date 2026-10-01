plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
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
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")

    implementation("dev.chrisbanes.haze:haze:1.3.1")
    implementation("dev.chrisbanes.haze:haze-materials:1.3.1")

    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")

    runtimeOnly("org.slf4j:slf4j-jdk14:2.0.18")
}

compose.desktop {
    application {
        mainClass = "com.skittlefm.bitchord.desktop.MainKt"
    }
}

tasks.register<JavaExec>("checkHome") {
    group = "verification"
    description = "Consulta o feed público usando o cliente desktop."

    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.skittlefm.bitchord.desktop.HomeCheck")
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("file.encoding", "UTF-8")
    systemProperty("stdout.encoding", "UTF-8")
    systemProperty("stderr.encoding", "UTF-8")
}

tasks.register<JavaExec>("checkDetail") {
    group = "verification"
    description = "Consulta as faixas de uma playlist ou álbum."

    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.skittlefm.bitchord.desktop.DetailCheck")
}