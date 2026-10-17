plugins {
    id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT"
    id("maven-publish")
}

version = "${property("mod.version")}+${property("minecraft")}"
group = property("mod.group") as String
base.archivesName = property("mod.id") as String

val requiredJava = JavaVersion.VERSION_25

repositories {
    // Only look in this repository for the given groups.
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft")}")
    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // Compiled against, and loaded in the development runs, but not a dependency of the published library.
    for (mod in listOf(property("deps.iris"), property("deps.sodium"))) {
        compileOnly("maven.modrinth:$mod")
        localRuntime("maven.modrinth:$mod")
    }

    // Iris nests these libraries in its jar, but the development runtime does not load nested jars. Without them
    // Iris fails as soon as it reads a shader pack.
    for (library in listOf(
        "org.anarres:jcpp:1.4.14",
        "org.antlr:antlr4-runtime:4.13.1",
        "io.github.douira:glsl-transformer:${property("deps.glsl_transformer")}"
    )) {
        localRuntime(library) { isTransitive = false }
    }

    // `-Pexit.superResolution` adds the Super Resolution mod to the development runs, to check the stretched merge.
    if (findProperty("exit.superResolution") != null) {
        localRuntime("maven.modrinth:${property("deps.super_resolution")}")
    }
}

// Development mod that checks every mixin applies. Run it with `runAuditClient`.
val audit: SourceSet = sourceSets.create("audit") {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

// Development mod that creates a world and checks the rendering. Run it with `runSmokeClient`.
val smoke: SourceSet = sourceSets.create("smoke") {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

loom {
    fabricModJsonPath = file("src/main/resources/fabric.mod.json") // Useful for interface injection
    mods {
        register(property("mod.id") as String) { sourceSet(sourceSets.main.get()) }
        register("emergency_exit_audit") { sourceSet(audit) }
        register("emergency_exit_smoke") { sourceSet(smoke) }
    }
    runs {
        register("auditClient") {
            client()
            source(audit)
            // Headless, so startup errors end the run instead of opening a dialog.
            vmArg("-Djava.awt.headless=true")
        }
        register("smokeClient") {
            client()
            source(smoke)
            // Headless, so startup errors end the run instead of opening a dialog. The game window still opens.
            vmArg("-Djava.awt.headless=true")
        }
    }
    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1") // Names lambdas, which helps with mixins
    }

    runConfigs.all {
        ideConfigGenerated(true)
        vmArgs("-Dmixin.debug.export=true") // Exports transformed classes for debugging
        runDir = "run"
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = requiredJava.majorVersion.toInt()
    }

    processResources {
        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.version,
            "minecraft" to project.property("mod.mc_dep"),
            "java" to requiredJava.majorVersion
        )
        inputs.properties(props)

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        inputs.property("mixinJava", mixinJava)
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    jar {
        val projectName = project.property("mod.id")
        inputs.property("projectName", projectName)
        from(file("LICENSE")) {
            rename { "${it}_$projectName" }
        }
    }

    // The audit reads the classes Mixin exports, so drop the ones from earlier runs.
    named("runAuditClient") {
        val exported = file("run/.mixin.out")
        doFirst { exported.deleteRecursively() }
    }

}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = property("mod.id") as String
            from(components["java"])
        }
    }
}
