import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.6.0"
    id("org.jetbrains.grammarkit") version "2022.3.2"  // JFlex + Grammar-Kit for custom DSL
}

group = "com.yourplugin"

val buildNumber: String = project.findProperty("buildNumber")?.toString() ?: "1"
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// ─── AWS SDK v2 + Kotlin Coroutines ────────────────────────────────────────────
val awsSdkVersion = "2.25.40"
val coroutinesVersion = "1.8.1"

dependencies {
    // IntelliJ Platform
    intellijPlatform {
        local("/Applications/IntelliJ IDEA.app")   // use the installed 2026.1 Ultimate
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.database")      // DatabaseIcons for tree node icons
        // com.intellij.ml.llm is optional (Ultimate-only); declared in plugin.xml, not needed here
    }

    // AWS SDK v2 — only what we need (avoid fat-jar)
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:sts")               // role assumption
    implementation("software.amazon.awssdk:sso")               // SSO profiles
    implementation("software.amazon.awssdk:ssooidc")
    implementation("software.amazon.awssdk:netty-nio-client")  // async HTTP client

    // Kotlin Coroutines (non-blocking AWS calls)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutinesVersion") // EDT bridging

    // JSON (for schema mapping & entity facets config)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")

    // Grammar-Kit generated sources (custom DSL)
    implementation("org.jetbrains:annotations:24.1.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}

intellijPlatform {
    pluginConfiguration {
        name = "DynamoDB Pro"
        version = project.version.toString()
        vendor {
            name = "Dawit Diriba"
            email = "dawitend2@gmail.com"
            url = "https://dynamodbpro.vercel.app"
        }
        description = """
            <b>DynamoDB Pro</b> is a powerful DynamoDB client plugin for IntelliJ IDEA.<br/><br/>

            <b>Features:</b>
            <ul>
                <li>Connect to AWS DynamoDB (local and remote) directly from your IDE</li>
                <li>Browse tables, indexes, and items in a tree view</li>
                <li>Run DQL (DynamoDB Query Language) queries with syntax highlighting and auto-completion</li>
                <li>View and paginate query results in an editor tab</li>
                <li>Export table data to CSV or JSON</li>
                <li>Create and delete tables and items</li>
                <li>Support for multiple simultaneous connections</li>
                <li>Live templates for common DynamoDB query patterns</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = "243"   // IntelliJ 2024.3
            untilBuild = "261.*" // IntelliJ 2026.1
        }
    }

    signing {
        certificateChain = System.getenv("CERTIFICATE_CHAIN")
        privateKey = System.getenv("PRIVATE_KEY")
        password = System.getenv("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = System.getenv("PUBLISH_TOKEN")
    }
}

// ─── Grammar-Kit: generate lexer + parser for the Smart Query DSL ─────────────
grammarKit {
    jflexRelease = "1.7.0-1"
    grammarKitRelease = "2022.3.2"
    intellijRelease = "243.21565.193"
}

sourceSets {
    main {
        java.srcDirs("src/main/gen")  // Grammar-Kit generated sources
    }
}

tasks {
    generateLexer {
        sourceFile = layout.projectDirectory.file("src/main/grammar/DynamoQuery.flex")
        targetDir = "src/main/gen/com/yourplugin/dynamodb/language/lexer"
        targetClass = "DynamoQueryLexer"
        purgeOldFiles = true
    }

    generateParser {
        sourceFile = layout.projectDirectory.file("src/main/grammar/DynamoQuery.bnf")
        targetRoot = "src/main/gen"
        pathToParser = "com/yourplugin/dynamodb/language/parser/DynamoQueryParser.java"
        pathToPsiRoot = "com/yourplugin/dynamodb/language/psi"
        // PathManager is required by newer platform internals used during parser generation.
        systemProperty("idea.home.path", "/Applications/IntelliJ IDEA.app/Contents")
        purgeOldFiles = true
    }

    withType<KotlinCompile> {
        dependsOn(generateLexer, generateParser)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview"
            )
        }
    }

    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    test {
        useJUnitPlatform()
    }

    named("instrumentCode") {
        enabled = false
    }

    named("instrumentTestCode") {
        enabled = false
    }

    // Inject the real version string into the packaged resources
    withType<ProcessResources> {
        filesMatching("version.properties") {
            expand("pluginVersion" to version)
        }
    }

    // Auto-increment buildNumber in gradle.properties after every successful plugin build
    named("buildPlugin") {
        doLast {
            val propsFile = file("gradle.properties")
            val next = buildNumber.toInt() + 1
            propsFile.writeText(propsFile.readText().replace("buildNumber=$buildNumber", "buildNumber=$next"))
            println("Build $version complete — next build will be 1.1.$next")
        }
    }

    // IntelliJ 2026.1 registers a custom NIO FileSystemProvider that must be on
    // the boot classpath before FileSystems initializes — otherwise the JVM crashes.
    val ideaHome = "/Applications/IntelliJ IDEA.app/Contents"
    val nioBootArgs = listOf(
        "-Xbootclasspath/a:$ideaHome/lib/nio-fs.jar",
        "-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider"
    )

    named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
        jvmArgs(nioBootArgs)
    }

    named<org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask>("buildSearchableOptions") {
        // This plugin has no Settings UI, so searchable options are unnecessary.
        enabled = false
    }


}
