package rs.ac.bg.etf.pp1;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import rs.ac.bg.etf.pp1.ast.Program;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class CompilerIntegrationTest {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration AST_TIMEOUT = Duration.ofSeconds(3);
    private static final Pattern RUNTIME_FOOTER = Pattern.compile("\\nCompletion took \\d+ ms\\n?\\z");

    private static final String SOURCE_FILE_NAME = "program.mj";
    private static final String INPUT_FILE_NAME = "input.txt";
    private static final String EXPECTED_OUTPUT_FILE_NAME = "expected.out";
    private static final String EXPECTED_OBJECT_FILE_NAME = "expected.obj";
    private static final String EXPECTED_DISASSEMBLY_FILE_NAME = "expected.disasm";

    private static final Path PROJECT_DIR = Path.of(System.getProperty("projectDir", ".")).toAbsolutePath().normalize();
    private static final Path INTEGRATION_TEST_RESOURCES = PROJECT_DIR.resolve("src").resolve("integrationTest").resolve("resources");
    private static final Path VALID_FIXTURES = INTEGRATION_TEST_RESOURCES.resolve("valid");
    private static final Path INVALID_FIXTURES = INTEGRATION_TEST_RESOURCES.resolve("invalid");
    private static final String JAVA_EXECUTABLE = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
    private static final String TEST_CLASSPATH = System.getProperty("java.class.path");

    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest(name = "{0}")
    @MethodSource("validFixtures")
    void validProgramProducesExpectedObjectAndOutput(String name, Fixture fixture) throws IOException {
        var objectFile = temporaryDirectory.resolve("program.obj");
        var expectedObjectFile = fixture.expectedObjectFile();
        var expectedOutputFile = fixture.expectedOutputFile();
        var expectedDisassemblyFile = fixture.expectedDisassemblyFile();

        List<String> failures = new ArrayList<>();
        var compiler = runProcess(
                command(
                        "rs.ac.bg.etf.pp1.Compiler",
                        fixture.source().toAbsolutePath().toString(),
                        objectFile.toAbsolutePath().toString()),
                temporaryDirectory,
                "");

        var compilerSucceeded = processSucceeded(compiler) && Files.exists(objectFile);
        if (compiler.failure() != null) {
            failures.add("Compiler could not be started: " + compiler.failure());
        } else if (compiler.timedOut()) {
            failures.add("Compiler timed out after " + PROCESS_TIMEOUT.toSeconds() + " seconds");
        } else if (compiler.exitCode() != 0) {
            failures.add("Compiler exited with code " + compiler.exitCode());
        } else if (!Files.exists(objectFile)) {
            failures.add("Compiler completed without creating an object file");
        }

        if (compilerSucceeded) {
            if (Files.exists(expectedObjectFile)) {
                compareObjectFiles(expectedObjectFile, objectFile, failures);
            } else {
                generateObjectSnapshot(objectFile, expectedObjectFile, failures);
            }
        } else if (!Files.exists(expectedObjectFile)) {
            failures.add("Missing expected object snapshot; it could not be generated because compilation failed: " + expectedObjectFile);
        }

        String actualOutput = null;
        var programOutputComparisonFailed = false;
        if (compilerSucceeded) {
            var input = Files.exists(fixture.inputFile())
                    ? Files.readString(fixture.inputFile(), StandardCharsets.UTF_8)
                    : "";
            var runtime = runProcess(
                    command("rs.etf.pp1.mj.runtime.Run", objectFile.toAbsolutePath().toString()),
                    temporaryDirectory,
                    input);
            actualOutput = programOutput(runtime.output());

            var runtimeSucceeded = processSucceeded(runtime);
            if (runtime.failure() != null) {
                failures.add("Runtime could not be started: " + runtime.failure());
            } else if (runtime.timedOut()) {
                failures.add("Runtime timed out after " + PROCESS_TIMEOUT.toSeconds() + " seconds");
            } else if (runtime.exitCode() != 0) {
                failures.add("Runtime exited with code " + runtime.exitCode());
            }

            if (runtimeSucceeded) {
                if (Files.exists(expectedOutputFile)) {
                    var expectedOutput = normalizeLineEndings(
                            Files.readString(expectedOutputFile, StandardCharsets.UTF_8));
                    if (!expectedOutput.equals(actualOutput)) {
                        programOutputComparisonFailed = true;
                        failures.add("Program output differs");
                    }
                } else {
                    generateOutputSnapshot(actualOutput, expectedOutputFile, failures);
                }
            } else if (!Files.exists(expectedOutputFile)) {
                failures.add("Missing expected output snapshot; it could not be generated because runtime execution failed: " + expectedOutputFile);
            }
        } else if (!Files.exists(expectedOutputFile)) {
            failures.add("Missing expected output snapshot; it could not be generated because compilation failed: " + expectedOutputFile);
        }

        String actualDisassembly = null;
        if (Files.exists(expectedDisassemblyFile)) {
            if (Files.exists(objectFile)) {
                var disassembly = disassemble(objectFile);
                actualDisassembly = disassembly.output();
                if (disassembly.failure() != null) {
                    failures.add("Disassembler could not be started: " + disassembly.failure());
                } else if (disassembly.timedOut()) {
                    failures.add("Disassembler timed out after " + PROCESS_TIMEOUT.toSeconds() + " seconds");
                } else if (disassembly.exitCode() != 0) {
                    failures.add("Disassembler exited with code " + disassembly.exitCode());
                } else {
                    var expectedDisassembly = normalizeLineEndings(Files.readString(expectedDisassemblyFile, StandardCharsets.UTF_8));
                    if (!expectedDisassembly.equals(normalizeLineEndings(actualDisassembly))) {
                        failures.add("Disassembly differs");
                    }
                }
            } else {
                failures.add("Expected disassembly exists, but compilation did not produce an object file: " + expectedDisassemblyFile);
            }
        }

        if (!failures.isEmpty()) {
            throw failureWithReport(name, failureReport(name, fixture, objectFile, compiler, actualOutput, actualDisassembly, programOutputComparisonFailed, failures));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFixtures")
    void invalidProgramDoesNotProduceObject(String name, Fixture fixture) {
        var objectFile = temporaryDirectory.resolve("program.obj");
        var compiler = runProcess(
                command(
                        "rs.ac.bg.etf.pp1.Compiler",
                        fixture.source().toAbsolutePath().toString(),
                        objectFile.toAbsolutePath().toString()),
                temporaryDirectory,
                "");

        List<String> failures = new ArrayList<>();
        if (compiler.failure() != null) {
            failures.add("Compiler could not be started: " + compiler.failure());
        } else if (compiler.timedOut()) {
            failures.add("Compiler timed out after " + PROCESS_TIMEOUT.toSeconds() + " seconds");
        } else if (compiler.exitCode() == 0) {
            failures.add("Invalid program was accepted");
        }
        if (Files.exists(objectFile)) {
            failures.add("Invalid program produced an object file");
        }

        if (!failures.isEmpty()) {
            throw failureWithReport(name, failureReport(name, fixture, objectFile, compiler, null, null, false, failures));
        }
    }

    static Stream<Arguments> validFixtures() throws IOException {
        return fixtures(VALID_FIXTURES).map(fixture -> Arguments.of(fixture.name(), fixture));
    }

    static Stream<Arguments> invalidFixtures() throws IOException {
        return fixtures(INVALID_FIXTURES).map(fixture -> Arguments.of(fixture.name(), fixture));
    }

    private static Stream<Fixture> fixtures(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Stream.empty();
        }
        try (var directories = Files.list(directory)) {
            return directories
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> new Fixture(path.getFileName().toString(), path))
                    .toList()
                    .stream();
        }
    }

    private static void compareObjectFiles(Path expectedFile, Path actualFile, List<String> failures) throws IOException {
        var expectedObject = Files.readAllBytes(expectedFile);
        var actualObject = Files.readAllBytes(actualFile);
        if (!Arrays.equals(expectedObject, actualObject)) {
            failures.add(String.format(
                    "Object file differs (expected %d bytes, %s; actual %d bytes, %s)",
                    expectedObject.length,
                    sha256(expectedObject),
                    actualObject.length,
                    sha256(actualObject)));
        }
    }

    private static void generateObjectSnapshot(Path actualFile, Path expectedFile, List<String> failures) {
        try {
            Files.copy(actualFile, expectedFile);
            failures.add("Expected object snapshot was missing; generated it at " + expectedFile + ". Review it and rerun the test.");
        } catch (IOException e) {
            failures.add("Expected object snapshot was missing and could not be generated at " + expectedFile + ": " + e);
        }
    }

    private static void generateOutputSnapshot(String output, Path expectedFile, List<String> failures) {
        try {
            Files.writeString(expectedFile, normalizeLineEndings(output), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            failures.add("Expected output snapshot was missing; generated it at " + expectedFile + ". Review it and rerun the test.");
        } catch (IOException e) {
            failures.add("Expected output snapshot was missing and could not be generated at " + expectedFile + ": " + e);
        }
    }

    private static ProcessResult disassemble(Path objectFile) {
        return runProcess(
                command("rs.etf.pp1.mj.runtime.disasm", objectFile.toAbsolutePath().toString()),
                objectFile.getParent(),
                "");
    }

    private static ProcessResult runProcess(List<String> command, Path workingDirectory, String input) {
        Process process = null;
        try (var readerExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory())) {
            try {
                var startedProcess = new ProcessBuilder(command)
                        .directory(workingDirectory.toFile())
                        .redirectErrorStream(true)
                        .start();
                process = startedProcess;

                var outputFuture = readerExecutor.submit(() -> startedProcess.getInputStream().readAllBytes());
                try (var stdin = startedProcess.getOutputStream()) {
                    stdin.write(input.getBytes(StandardCharsets.UTF_8));
                }

                var finished = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    process.waitFor(1, TimeUnit.SECONDS);
                }

                String output;
                try {
                    output = new String(outputFuture.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8);
                } catch (ExecutionException | TimeoutException e) {
                    output = "[Could not collect process output: " + e.getMessage() + "]";
                }

                return new ProcessResult(finished ? process.exitValue() : -1, !finished, output, null);
            } catch (IOException e) {
                return new ProcessResult(-1, false, "", e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                return new ProcessResult(-1, false, "", "Interrupted while waiting for process");
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
                readerExecutor.shutdownNow();
            }
        }
    }

    private static boolean processSucceeded(ProcessResult result) {
        return result.failure() == null && !result.timedOut() && result.exitCode() == 0;
    }

    private static String failureReport(String name, Fixture fixture, Path objectFile, ProcessResult compiler, String actualOutput,
                                        String actualDisassembly, boolean programOutputComparisonFailed, List<String> failures) {
        var report = new StringBuilder();
        report.append("Integration test failed: ").append(name).append('\n')
                .append("Source: ")
                .append(fixture.source())
                .append("\n\n")
                .append("=== FAILURES ===\n");

        failures.forEach(failure -> report.append("- ").append(failure).append('\n'));

        report.append("\n=== AST ===\n").append(astDump(fixture.source()))
                .append("\n=== DISASSEMBLY ===\n").append(disassemblyForFailure(objectFile, actualDisassembly))
                .append("\n=== COMPILER OUTPUT ===\n").append(compiler.output());

        if (programOutputComparisonFailed) {
            report.append("\n=== EXPECTED PROGRAM OUTPUT ===\n").append(expectedArtifact(fixture.expectedOutputFile()))
                    .append("\n=== ACTUAL PROGRAM OUTPUT ===\n")
                    .append(actualOutput == null ? "[Unavailable]\n" : actualOutput);
        }

        return report.toString();
    }

    private static AssertionError failureWithReport(String name, String report) {
        System.err.print(report);
        if (!report.endsWith(System.lineSeparator())) {
            System.err.println();
        }
        return new AssertionError("See detailed integration test report in test output: " + name);
    }

    private static String expectedArtifact(Path artifact) {
        if (!Files.exists(artifact)) {
            return "[Unavailable: " + artifact.getFileName() + " does not exist]\n";
        }
        try {
            return normalizeLineEndings(Files.readString(artifact, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return "[Unavailable: could not read " + artifact.getFileName() + ": " + e + "]\n";
        }
    }

    private static String disassemblyForFailure(Path objectFile, String actualDisassembly) {
        if (actualDisassembly != null) {
            return actualDisassembly;
        }
        if (!Files.exists(objectFile)) {
            return "[Unavailable: no generated object file]\n";
        }

        var disassembly = disassemble(objectFile);
        if (disassembly.failure() != null) {
            return "[Unavailable: " + disassembly.failure() + "]\n";
        }
        if (disassembly.timedOut()) {
            return "[Unavailable: disassembler timed out]\n";
        }
        return disassembly.output();
    }

    private static String astDump(Path source) {
        try (var executor = Executors.newSingleThreadExecutor(daemonThreadFactory())) {
            var astFuture = executor.submit(() -> parseAst(source));
            try {
                return astFuture.get(AST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "[Unavailable: interrupted while parsing AST]\n";
            } catch (ExecutionException | TimeoutException e) {
                astFuture.cancel(true);
                return "[Unavailable: " + e.getMessage() + "]\n";
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static String parseAst(Path source) {
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            var lexer = new Yylex(reader);
            var parser = new MJParser(lexer);
            parser.loggingEnabled = false;
            var root = parser.parse();
            if (root == null || !(root.value instanceof Program program)) {
                return "[Unavailable: parser did not produce a Program AST]\n";
            }
            return program.toString("");
        } catch (Exception e) {
            return "[Unavailable: " + e + "]\n";
        }
    }

    private static List<String> command(String mainClass, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(JAVA_EXECUTABLE);
        command.add("-cp");
        command.add(TEST_CLASSPATH);
        command.add(mainClass);
        command.addAll(List.of(arguments));
        return command;
    }

    private static String programOutput(String runtimeOutput) {
        var normalized = normalizeLineEndings(runtimeOutput);
        var matcher = RUNTIME_FOOTER.matcher(normalized);
        return matcher.find() ? normalized.substring(0, matcher.start()) : normalized;
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            var thread = new Thread(runnable, "mj-integration-reader");
            thread.setDaemon(true);
            return thread;
        };
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private record Fixture(String name, Path directory) {

        Path source() {
            return directory.resolve(SOURCE_FILE_NAME);
        }

        Path inputFile() {
            return directory.resolve(INPUT_FILE_NAME);
        }

        Path expectedOutputFile() {
            return directory.resolve(EXPECTED_OUTPUT_FILE_NAME);
        }

        Path expectedObjectFile() {
            return directory.resolve(EXPECTED_OBJECT_FILE_NAME);
        }

        Path expectedDisassemblyFile() {
            return directory.resolve(EXPECTED_DISASSEMBLY_FILE_NAME);
        }
    }

    private record ProcessResult(int exitCode, boolean timedOut, String output, String failure) {
    }
}
