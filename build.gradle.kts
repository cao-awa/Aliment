import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.scala.ScalaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20"
    scala
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("aliment") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}


repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
    //
    // Scala itself is not a Minecraft or Fabric artifact, so it needs the real Maven Central.
    mavenCentral()
    maven {
        name = "Jared's maven"
        url = uri("https://maven.blamejared.com/")
    }
    // Farmer's Delight is not on any of the mod mavens - not Jared's, not modmaven, not Shedaniel's -
    // and not on CurseMaven either. It is published on Modrinth's maven, whose layout is
    // `maven.modrinth:<project slug>:<version>`. The group filter keeps every other lookup away from
    // it, so nothing else can start silently resolving from there.
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
}

/** The Scala runtime jars, merged into the mod jar. Declared before the dependencies that use it. */
val scalaRuntime: Configuration = configurations.create("scalaRuntime")

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // The Scala runtime. The numerical model in src/model/scala is the only thing that uses it, but
    // it has to ship with the mod.
    //
    // Note the two artifacts. Since Scala 3.9, `org.scala-lang:scala3-library_3` is a 344-byte stub
    // with an empty MANIFEST and the standard library - `scala.Product`, `scala.Predef` and the rest
    // - actually lives in `org.scala-lang:scala-library`. The compiler still wants the former on its
    // classpath (without it Zinc fails to load), so both are declared explicitly rather than letting
    // one arrive transitively.
    //
    // The runtime is shaded into the mod jar rather than nested with `include`: Loom's `include`
    // wraps a library that is not itself a Fabric mod in generated metadata, and in this Loom version
    // the wrapper came out with no classes in it at all - a silently broken jar.
    implementation("org.scala-lang:scala3-library_3:${project.property("scala_version")}")
    implementation("org.scala-lang:scala-library:${project.property("scala_version")}")
    add("scalaRuntime", "org.scala-lang:scala3-library_3:${project.property("scala_version")}")
    add("scalaRuntime", "org.scala-lang:scala-library:${project.property("scala_version")}")

    // JEI (Just Enough Items) API integration
    compileOnly("mezz.jei:jei-26.3-fabric-api:31.9.0.56")

    // Farmer's Delight, for the optional food integration: every edible item it adds is given
    // Aliment values in `compat/farmersdelight`. Compile-time only, so the mod still builds and runs
    // with no Farmer's Delight installed, and the released jar neither ships nor demands it.
    //
    // The jar is unobfuscated - Minecraft 26.3 is unmapped, so a Fabric mod for it names
    // `net.minecraft.world.item.Item` rather than an intermediary `class_1799` - which is why this
    // needs no remapping and can be a plain `compileOnly` rather than a Loom mod configuration.
    compileOnly("maven.modrinth:farmers-delight-refabricated:${project.property("farmersdelight_version")}")

    // ... and in a development run only, the real thing, so the integration can be exercised and
    // tested against the actual items rather than against a guess at them.
    localRuntime("maven.modrinth:farmers-delight-refabricated:${project.property("farmersdelight_version")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version")!!,
            "loader_version" to project.property("loader_version")!!,
            "kotlin_loader_version" to project.property("kotlin_loader_version")!!
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.additionalParameters = listOf("-deprecation", "-feature")
}

// The numerical model is Scala and lives in `src/main/scala`, so the Kotlin half and the Scala half
// are one source set and one module - which is also what the IDE wants.
//
// The build order has to be
//
//   scala  ->  kotlin  ->  java (the mixins)
//
// because the Kotlin files call into the model and the mixins call into Kotlin. The Scala plugin's
// own `compileScala` cannot be the first step: it sets up joint Java/Scala compilation, so it
// depends on `compileJava`, and that closes the cycle
// `compileJava -> compileKotlin -> compileScala -> compileJava`. The dependency is unconditional -
// it survives the source set having no Java sources at all - and clearing it from the task's
// `dependsOn` does not stick, because the plugin re-adds it when the task is realised.
//
// So the model gets a task of its own, with no Java and no dependency on `compileJava`, writing to
// the conventional Scala classes directory so that everything else picks it up normally.
val scalaClasses = layout.buildDirectory.dir("classes/scala/main")
val compileModelScala = tasks.register<ScalaCompile>("compileModelScala") {
    description = "Compiles the numerical model in src/main/scala."
    group = "build"
    source(fileTree("src/main/scala"))
    destinationDirectory.set(scalaClasses)
    // The model needs the Scala library and the JDK. It deliberately does not reference Minecraft,
    // Kotlin or our Java, and this classpath is what the compiler checks that against. It is the
    // `compileClasspath` *configuration* rather than the source set's classpath, because the latter
    // carries this very task's output (see below) and that would be a self dependency.
    classpath = configurations["compileClasspath"]
    // The Scala plugin sets these by convention on the tasks it creates itself; a hand-made one has
    // to say where Zinc keeps its incremental state, and what bytecode level to target.
    scalaCompileOptions.incrementalOptions.analysisFile.set(
        layout.buildDirectory.file("tmp/scala/compileModelScala.analysis"),
    )
    scalaCompileOptions.incrementalOptions.classfileBackupDir =
        layout.buildDirectory.dir("tmp/scala/compileModelScala-backup").get().asFile
    // Without this the task inherits Java 8 as its output level and Zinc refuses the flag.
    targetCompatibility = targetJavaVersion.toString()
    javaLauncher.set(
        javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(targetJavaVersion)) },
    )
}

// The plugin's own task would compile the same sources again, jointly with Java. Empty it.
tasks.named<ScalaCompile>("compileScala") {
    setSource(files())
}

// Everything downstream sees the model's classes as an ordinary part of this source set.
sourceSets["main"].compileClasspath += files(compileModelScala)
sourceSets["main"].runtimeClasspath += files(compileModelScala)
sourceSets["client"].compileClasspath += files(compileModelScala)
sourceSets["client"].runtimeClasspath += files(compileModelScala)

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(compileModelScala)
}

tasks.named<KotlinCompile>("compileClientKotlin") {
    dependsOn(compileModelScala)
}

// Scala compiles into the main source set's classes, so the jar needs that directory explicitly.
// The sources jar does not need anything: `src/main/scala` is part of this source set now.
tasks.jar {
    from(scalaClasses)
}

// IntelliJ builds a Gradle-imported module with its own compiler when "Build and run using" is
// *IntelliJ IDEA*, and copies `src/main/resources` into the module's compiler output - which for this
// project is Gradle's `build/classes/java/main`. The classes directory comes before the resources
// directory on the classpath, so those copies shadow the ones `processResources` produced:
//
//  * `fabric.mod.json` among them is the raw file, so it still says `${version}` and a dev run dies
//    with "Some of your mods are incompatible with the game or each other!";
//  * every other file is a second copy of one `build/resources/main` already has, which makes `jar`
//    fail on a duplicate entry (and would make the mod jar carry every texture twice).
//
// So both are dealt with twice over: the copies are deleted as the last step before any run or jar,
// and `jar` refuses a resource path that came from `build/classes` whatever happens. Deleting leaves
// empty `assets/` and `data/` directories behind - `delete` only ever removes files - and those are
// harmless, because the resources directory still supplies the real ones.
//
// The lasting fix is a one-setting change in the IDE - Gradle rather than IntelliJ for build and run
// - but nothing in the build can make the IDE stop writing there, so the guards stay.
val classesRoot = layout.buildDirectory.dir("classes").get().asFile.absolutePath

val ideResourceCopies = fileTree(layout.buildDirectory.dir("classes").get()) {
    include("**/fabric.mod.json", "**/*.mixins.json", "**/assets/**", "**/data/**")
}

/** Always runs: its whole job is to undo a file the IDE may have written at any time. */
val dropIdeResourceCopies = tasks.register("dropIdeResourceCopies") {
    description = "Deletes the raw resource copies IntelliJ leaves in the compiled classes directories."
    group = "build"
    outputs.upToDateWhen { false }
    doLast {
        delete(ideResourceCopies)
    }
}

tasks.named("processResources") {
    finalizedBy(dropIdeResourceCopies)
}

tasks.matching { it.name.startsWith("run") }.configureEach {
    dependsOn(dropIdeResourceCopies)
    // And again as the very last step, in case the IDE wrote it again while the classpath was built.
    doFirst {
        delete(ideResourceCopies)
    }
}

tasks.jar {
    dependsOn(dropIdeResourceCopies)

    exclude(
        object : Spec<FileTreeElement> {
            override fun isSatisfiedBy(element: FileTreeElement): Boolean {
                if (!element.file.absolutePath.startsWith(classesRoot)) {
                    return false
                }
                val path = element.relativePath.pathString
                return path == "fabric.mod.json" ||
                    path.endsWith(".mixins.json") ||
                    path.startsWith("assets/") ||
                    path.startsWith("data/")
            }
        },
    )
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }

    // The Scala runtime, merged in. Signature files and module metadata have to go: a shaded jar
    // with two MANIFEST.MF files, or a stale signature, is a jar that will not load.
    from(scalaRuntime.elements.map { jars -> jars.map { if (it.asFile.isDirectory) it.asFile else zipTree(it.asFile) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "META-INF/versions/**")
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
