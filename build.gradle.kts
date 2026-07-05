plugins {
    java
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val specificationDir = "spec"
val librariesDir = "lib"
val sourceDir = "src"
val testDir = "test"
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

val libraries = listOf(
    jFlexJar,
    cupJar,
    "log4j-1.2.17.jar",
    "symboltable-1-1.jar",
    mjRuntimeJar
)

dependencies {
    libraries.forEach { library ->
        implementation(files("$librariesDir/$library"))
    }
}

sourceSets {
    main {
        java {
            srcDirs(sourceDir, generatedSourceDir)
        }
        resources {
            srcDirs("config")
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    outputs.cacheIf { true }
}

val lexerGen = tasks.register<JavaExec>("lexerGen") {
    group = "compilation"
    description = "Generates the lexer"

    classpath = files("$librariesDir/$jFlexJar")
    mainClass.set("JFlex.Main")

    inputs.file("$specificationDir/mjlexer.lex")
    outputs.file(generatedSourcePackageDir.file("Yylex.java") )

    args(
        "-d", generatedSourcePackageDir.asFile.path,
        "$specificationDir/mjlexer.lex"
    )
}

val parserGen = tasks.register<JavaExec>("parserGen") {
    dependsOn(lexerGen)

    group = "compilation"
    description = "Generates the parser"

    workingDir = generatedSourceDir.get().asFile
    classpath = files("$librariesDir/$cupJar")
    mainClass.set("java_cup.Main")

    val cupFileName = "mjparser.cup"
    val cupFile = generatedSourceDir.get().asFile.resolve(cupFileName)

    val astOutputDir = generatedSourcePackageDir.dir("ast")
    inputs.file("$specificationDir/$cupFileName")
    outputs.dir(astOutputDir)
    outputs.files(
        astOutputDir.file("MJParser.java"),
        astOutputDir.file("sym.java"),
        astOutputDir.file("${cupFileName.removeSuffix(".cup")}_astbuild.cup"),
    )

    doFirst {
        astOutputDir.asFile.mkdirs()
        file("$specificationDir/$cupFileName").copyTo(cupFile, overwrite = true)
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

application {
    mainClass.set("$mainPackage.Compiler")
}

tasks.register<JavaExec>("mjCompile") {
    dependsOn("classes")

    group = "compilation"

    val sourceFile = project.findProperty("sourceFile")?.toString() ?: sourceFileName
    description = "Compiles an MJ program written in '$testDir/$sourceFile'"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("$mainPackage.Compiler")

    inputs.file("$testDir/$sourceFile")
    outputs.file(outputFile)

    args("$testDir/$sourceFile", outputFile.get().asFile.absolutePath)
}

tasks.register<JavaExec>("runObj") {
    group = "execution"
    description = "Runs an MJ program previously generated compiled from '$testDir/$sourceFileName'"

    classpath = files("$librariesDir/$mjRuntimeJar")
    mainClass.set("$mjRuntimePackage.Run")

    inputs.file(outputFile)
    args(outputFile.get().asFile.absolutePath)
    standardInput = System.`in`
}

tasks.register<JavaExec>("disasm") {
    group = "execution"
    description = "Disassembles an MJ program previously generated compiled from '$testDir/$sourceFileName'"

    classpath = files("$librariesDir/$mjRuntimeJar")
    mainClass.set("$mjRuntimePackage.disasm")

    inputs.file(outputFile)
    args(outputFile.get().asFile.absolutePath)
}

tasks.register<JavaExec>("debugObj") {
    dependsOn("disasm")

    group = "execution"
    description = "Runs an MJ program previously generated compiled from '$testDir/$sourceFileName' with debugging enabled"

    classpath = files("$librariesDir/$mjRuntimeJar")
    mainClass.set("$mjRuntimePackage.Run")

    inputs.file(outputFile)
    args(outputFile.get().asFile.absolutePath, "-debug")
    standardInput = System.`in`
}
