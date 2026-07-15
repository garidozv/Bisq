import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    application
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val specificationDir = layout.projectDirectory.dir("spec")
val librariesDir = layout.projectDirectory.dir("lib")
val mjRuntimePackage = "rs.etf.pp1.mj.runtime"
val mainPackage = "rs.ac.bg.etf.pp1"
val relativePackagePath = mainPackage.replace(".", "/")
val generatedDir = layout.buildDirectory.dir("generated")
val generatedSourceDir = generatedDir.map { it.dir("sources") }
val generatedSourcePackageDir = generatedSourceDir.get().dir(relativePackagePath)

val sourceFileName = "program.mj"
val defaultOutputFileName = "program.obj"
val outputFile = generatedDir.map { it.file(defaultOutputFileName) }

val jFlexJar = "JFlex.jar"
val cupJar = "cup_v10k.jar"
val mjRuntimeJar = "mj-runtime-1.1.jar"
val log4jJar = "log4j-1.2.17.jar"
val symbolTableJar = "symboltable-1-1.jar"

val lexerClasspath = files(librariesDir.file(jFlexJar))
val parserClasspath = files(librariesDir.file(cupJar))
val runtimeClasspath = files(
    librariesDir.file(cupJar),
    librariesDir.file(log4jJar),
    librariesDir.file(symbolTableJar),
    librariesDir.file(mjRuntimeJar),
)

sourceSets {
    main {
        java.srcDir(generatedSourceDir)
    }

    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += output + sourceSets.main.get().runtimeClasspath
    }
}

configurations {
    named("integrationTestImplementation") {
        extendsFrom(testImplementation.get())
    }
    named("integrationTestRuntimeOnly") {
        extendsFrom(testRuntimeOnly.get())
    }
}

dependencies {
    implementation(runtimeClasspath)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaExec>().configureEach {
    outputs.cacheIf { true }
}

val lexerGen = tasks.register<JavaExec>("lexerGen") {
    group = "compilation"
    description = "Generates the lexer"

    classpath = lexerClasspath
    mainClass.set("JFlex.Main")

    inputs.file(specificationDir.file("mjlexer.lex"))
    outputs.file(generatedSourcePackageDir.file("Yylex.java"))

    args(
        "-d", generatedSourcePackageDir.asFile.path,
        specificationDir.file("mjlexer.lex").asFile.absolutePath
    )
}

val parserGen = tasks.register<JavaExec>("parserGen") {
    dependsOn(lexerGen)

    group = "compilation"
    description = "Generates the parser"

    workingDir = generatedSourceDir.get().asFile
    classpath = parserClasspath
    mainClass.set("java_cup.Main")

    val cupFileName = "mjparser.cup"
    val cupFile = generatedSourceDir.get().file(cupFileName).asFile

    val astOutputDir = generatedSourcePackageDir.dir("ast")
    inputs.file(specificationDir.file(cupFileName))
    outputs.dir(astOutputDir)
    outputs.files(
        astOutputDir.file("MJParser.java"),
        astOutputDir.file("sym.java"),
        generatedSourceDir.get().file("${cupFileName.removeSuffix(".cup")}_astbuild.cup"),
    )

    doFirst {
        astOutputDir.asFile.mkdirs()
        specificationDir.file(cupFileName).asFile.copyTo(cupFile, overwrite = true)
    }

    args(
        "-destdir", generatedSourcePackageDir.asFile.path,
        "-ast", "$mainPackage.ast",
        "-parser", "MJParser",
        "-buildtree",
        cupFile.absolutePath
    )
}

tasks.compileJava {
    dependsOn(parserGen)
}

tasks.test {
    useJUnitPlatform()
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs compiler integration tests"
    group = "verification"

    val integrationTestSourceSet = sourceSets.getByName("integrationTest")
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    testLogging {
        exceptionFormat = TestExceptionFormat.SHORT
        showStackTraces = false
        showStandardStreams = true
    }
    systemProperty("projectDir", project.projectDir.absolutePath)
    inputs.dir(layout.projectDirectory.dir("src/integrationTest/resources"))
    dependsOn("classes")
    shouldRunAfter(tasks.test)
}

tasks.named("check") {
    dependsOn(integrationTest)
}

application {
    mainClass.set("$mainPackage.Compiler")
}

tasks.register<JavaExec>("mjCompile") {
    dependsOn("classes")

    group = "compilation"

    val sourceFile = project.file(project.findProperty("sourceFile")?.toString() ?: sourceFileName)
    description = "Compiles an MJ program written in '${sourceFile.relativeTo(project.projectDir)}'"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("$mainPackage.Compiler")

    inputs.file(sourceFile)
    outputs.file(outputFile)

    args(sourceFile.absolutePath, outputFile.get().asFile.absolutePath)
}

fun JavaExec.configureRuntimeTask() {
    group = "execution"
    classpath = runtimeClasspath
    mainClass.set("$mjRuntimePackage.Run")
    inputs.file(outputFile)
    args(outputFile.get().asFile.absolutePath)
}

tasks.register<JavaExec>("runObj") {
    group = "execution"
    description = "Runs the generated MJ program"
    configureRuntimeTask()
    standardInput = System.`in`
}

tasks.register<JavaExec>("disasm") {
    group = "execution"
    description = "Disassembles the generated MJ program"
    configureRuntimeTask()
    mainClass.set("$mjRuntimePackage.disasm")
}

tasks.register<JavaExec>("debugObj") {
    dependsOn("disasm")
    group = "execution"
    description = "Runs the generated MJ program with debugging enabled"
    configureRuntimeTask()
    args("-debug")
    standardInput = System.`in`
}
