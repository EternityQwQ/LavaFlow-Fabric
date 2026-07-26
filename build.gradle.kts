plugins {
    java
    application
}

group = "dev.lavaflow"
version = "0.1.0-SNAPSHOT"

val lwjglVersion = "3.4.1"
val lwjglArch = System.getProperty("os.arch").lowercase()
val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") && lwjglArch in setOf("aarch64", "arm64") ->
        "natives-windows-arm64"
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name").startsWith("Mac") && lwjglArch in setOf("aarch64", "arm64") ->
        "natives-macos-arm64"
    System.getProperty("os.name").startsWith("Mac") -> "natives-macos"
    lwjglArch in setOf("aarch64", "arm64") -> "natives-linux-arm64"
    else -> "natives-linux"
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") {
        content {
            includeGroup("net.fabricmc")
        }
    }
}

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-shaderc")
    implementation("org.lwjgl:lwjgl-spvc")
    implementation("org.lwjgl:lwjgl-vma")
    implementation("org.lwjgl:lwjgl-vulkan")
    implementation("org.joml:joml:1.10.8")

    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-shaderc::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-spvc::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-vma::$lwjglNatives")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

application {
    mainClass = "dev.lavaflow.smoke.LavaFlowSmoke"
}

// Signature-only stubs for the Sodium classes the compatibility mixins target. Sodium supplies the
// real classes at runtime, so this output is never packaged.
val sodiumStub by sourceSets.creating {
    java.setSrcDirs(listOf("src/sodiumStub/java"))
    resources.setSrcDirs(emptyList<String>())
    compileClasspath += configurations.compileClasspath.get() + files("refs/Minecraft26.2Client.jar")
}

val minecraft by sourceSets.creating {
    java.setSrcDirs(listOf("src/minecraft/java"))
    resources.setSrcDirs(listOf("src/minecraft/resources"))
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get() + sodiumStub.output
    runtimeClasspath += output + compileClasspath
}

configurations[minecraft.implementationConfigurationName].extendsFrom(configurations.implementation.get())

dependencies {
    add(minecraft.implementationConfigurationName, files("refs/Minecraft26.2Client.jar"))
    add(minecraft.compileOnlyConfigurationName, "net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.named<JavaCompile>(minecraft.compileJavaTaskName) {
    options.release = 25
}

tasks.named<JavaCompile>(sodiumStub.compileJavaTaskName) {
    options.release = 25
}

tasks.jar {
    from(minecraft.output) {
        exclude("dev/lavaflow/natives/**")
    }
    from("LICENSE") {
        into("META-INF")
    }
    manifest.attributes(
        "Implementation-Title" to "LavaFlow",
        "Implementation-Version" to project.version
    )
}

tasks.test {
    useJUnitPlatform()
}
