plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.loom)
    alias(libs.plugins.dokka)
    `maven-publish`
}

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val minecraft = stonecutter.current.version
val lib = catalogs.named("libs${minecraft.replace(".", "")}")
val mod = catalogs.named("mod")

version = "${mod("version")}+$minecraft"
group = mod("group")
base.archivesName = mod("id")

repositories {
    mavenCentral()
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://maven.starred.foo/releases")
    maven("https://maven.starred.foo/snapshots")
    maven("https://maven.meteordev.org/releases") { name = "meteor-maven" }
}

// Test mods (Java + Kotlin) that consume Astra like a real mod would. Never published.
val testmod: SourceSet = sourceSets.create("testmod") {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}
// Compile-time checks for Java mods (annotation processor). Plain Java, no dependencies; shipped
// inside the AstraConfig jar so mods can use `annotationProcessor("maven.modrinth:astraconfig:<version>")`.
val processor: SourceSet = sourceSets.create("processor")


dependencies {
    minecraft(lib["minecraft"])

    localRuntime(libs.devauth)

    implementation(lib["fabric-api"])
    implementation(libs.fabric.loader)
    implementation(libs.fabric.language.kotlin)

    shadow(libs.kbus)
    shadow(libs.kommand)
    shadow(libs.orbit)
    shadow(libs.snowbird.find())
    shadow(libs.cascade.find())
    shadow(libs.jsvg) // lets Cascade rasterize SVG icons

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    "testmodAnnotationProcessor"(processor.output)
    testImplementation(processor.output)
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    accessWidenerPath = rootProject.file("src/main/resources/${mod("id")}.accesswidener")

    mods {
        register(mod("id")) { sourceSet(sourceSets.main.get()) }
        register("astraconfig_testmod") { sourceSet(testmod) }
    }

    runConfigs.named("client") {
        generateRunConfig = true
        jvmArguments.addAll("-Ddevauth.enabled=true", "-Ddevauth.account=main", "-XX:+AllowEnhancedClassRedefinition", "-XX:+IgnoreUnrecognizedVMOptions")
    }

    runs {
        register("testmodClient") {
            client()
            name = "Testmod Client"
            source(testmod)
            vmArgs("-XX:+AllowEnhancedClassRedefinition", "-XX:+IgnoreUnrecognizedVMOptions")
        }
    }

    runConfigs.named("server") {
        generateRunConfig = false
    }
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(25)
    // The Kotlin test mod's widget gallery uses Astra's internal option views.
    target.compilations.named("testmod") { associateWith(target.compilations.getByName("main")) }

    // Public API snapshot: `updateKotlinAbi` writes it, `checkKotlinAbi` (part of `check`) fails on accidental changes.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { }
}

// API docs: `./gradlew :26.1:dokkaGenerate` -> build/dokka/html. Only the public API of `main`.
dokka {
    moduleName.set("AstraConfig")
    dokkaSourceSets.configureEach {
        if (name == "test" || name == "testmod") suppress.set(true)
        perPackageOption {
            matchingRegex.set("xyz\\.essencexc\\.astra\\.(core|mixin)(\\..*)?")
            suppress.set(true)
        }
    }
}

publishing {
    // mavenLocal only for now: `./gradlew publishToMavenLocal`
    publications {
        create<MavenPublication>("maven") {
            groupId = mod("group")
            artifactId = mod("id")
            version = "${mod("version")}+$minecraft"
            from(components["java"])
        }
    }
}

tasks {
    jar {
        from(processor.output)
        from(rootProject.file("LICENSE"), rootProject.file("LICENSE-TEMPLATE"))
    }

    // The test mods as a normal mod jar, e.g. to try AstraConfig outside the dev setup: build/libs/astraconfig-testmod-*.jar
    register<Jar>("testmodJar") {
        description = "Packages the Java and Kotlin test mods as a mod jar."
        group = "build"
        archiveBaseName.set("astraconfig-testmod")
        archiveVersion.set("${mod("version")}+$minecraft")
        from(testmod.output)
    }

    // The Java test mod is checked by the processor at compile time.
    named("compileTestmodJava") { dependsOn("compileProcessorJava") }

    processResources {
        val r = mapOf("id" to mod("id"), "name" to mod("name"), "version" to mod("version"), "minecraft" to lib("compatibility"), "tweaker" to mod("tweaker"))

        inputs.properties(r)
        filesMatching("fabric.mod.json") { expand(r) }
    }

    test {
        useJUnitPlatform()
    }

    register<Copy>("buildAndCollect") {
        description = "Builds and collects mod jars."
        group = "build"
        from(jar, kotlinSourcesJar)
        into(rootProject.layout.buildDirectory.file("libs/${mod("version")}"))
        dependsOn("build")
    }
}

fun DependencyHandlerScope.shadow(dep: Any, config: ExternalModuleDependency.() -> Unit = {}) {
    val d = create((dep as? Provider<*>)?.get() ?: dep) as ExternalModuleDependency
    d.config()
    include(d)
    implementation(d)
}

fun Provider<MinimalExternalModuleDependency>.find(): String {
    return "${get()}+$minecraft"
}

operator fun VersionCatalog.get(name: String): Provider<MinimalExternalModuleDependency> {
    return findLibrary(name).get()
}

operator fun VersionCatalog.invoke(name: String): String {
    return findVersion(name).get().requiredVersion
}
