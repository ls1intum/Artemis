package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Shared test harness for driving the EXACT collect step the shipped {@code verify.sh} contains: it renders the live script via {@link SandboxBuildCommandService}, slices the
 * report-collection region out of it (so the shell under test is byte-identical to what ships), and runs that region under a real POSIX {@code sh} against a fixture build tree. It
 * then returns the files the script COLLECTED into the verifier-owned reports dir (keyed by their flat {@code <seq>__<canonical>} name), which the verifier would copy out and
 * parse
 * with the production parsers — so a test can feed the collected JUnit report straight into {@code TestResultXmlParser} and prove parity-by-construction.
 */
final class VerifyScriptTestHarness {

    private VerifyScriptTestHarness() {
    }

    /** Writes a UTF-8 text fixture via Apache {@link FileUtils} (the arch-mandated replacement for {@code Files.write*}), creating any missing parent directories. */
    static void writeString(Path path, CharSequence content) throws IOException {
        FileUtils.writeStringToFile(path.toFile(), content.toString(), StandardCharsets.UTF_8);
    }

    /** The full text of the live generated {@code verify.sh} for a default (phase-less) exercise, the source of every sliced snippet below. */
    static String verifyScript() {
        return new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
    }

    /** The full text of the live generated {@code verify.sh} for the given exercise (used to exercise the SCA collection for an SCA-enabled exercise). */
    static String verifyScript(SandboxBuildCommandService service, ProgrammingExercise exercise) {
        return service.verifyScriptContent(exercise);
    }

    /**
     * Slices the half-open region {@code [start, endInclusive)} out of the live {@code verify.sh}, where the region runs from the FIRST occurrence of {@code startMarker} through
     * the end of the first occurrence of {@code endMarker} at or after it. Asserts both markers are present so a drifting snippet boundary fails loudly.
     */
    static String slice(String script, String startMarker, String endMarker) {
        int start = script.indexOf(startMarker);
        assertThat(start).as("snippet start marker '%s' present in verify.sh", startMarker).isNotNegative();
        int end = script.indexOf(endMarker, start);
        assertThat(end).as("snippet end marker '%s' present in verify.sh after the start", endMarker).isGreaterThan(start);
        return script.substring(start, end + endMarker.length());
    }

    /** Runs {@code sh <scriptFile>} (stderr merged into stdout) with a 30s timeout and returns its combined output. */
    static String runSh(Path scriptFile) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("verify.sh snippet did not finish in time");
        }
        return output;
    }

    /** Creates the {@code .hyperion-build-start} marker with an mtime an hour in the past, so any report written afterwards is strictly newer and is collected. */
    static Path staleBuildStartMarker(Path buildDir) throws IOException {
        Path marker = buildDir.resolve(".hyperion-build-start");
        writeString(marker, "");
        Files.setLastModifiedTime(marker, FileTime.from(Instant.now().minusSeconds(3600)));
        return marker;
    }

    /**
     * Runs the live JUnit-and-SCA collect step (sliced from the generated script) against a fixture build tree and returns the files it collected into the reports dir, keyed by
     * their flat collected name. The collected JUnit report can then be fed straight into the production {@code TestResultXmlParser}, so the test proves the verifier parses
     * exactly
     * what the script collected.
     *
     * @param service    the build-command service whose live script provides the collect step (use an SCA-enabled exercise to also exercise SCA collection)
     * @param exercise   the exercise whose script is rendered
     * @param tempDir    the per-test temporary directory
     * @param name       a unique name for this run's build subtree
     * @param buildFiles files to write into the build tree BEFORE collection (relative path -> content), e.g. {@code test-results/results.xml}
     * @return the collected files keyed by their flat {@code <seq>__<canonical>} name
     */
    static Map<String, String> collect(SandboxBuildCommandService service, ProgrammingExercise exercise, Path tempDir, String name, Map<String, String> buildFiles)
            throws IOException, InterruptedException {
        Path buildDir = Files.createDirectories(tempDir.resolve(name).resolve("build"));
        Path reportsParent = Files.createDirectories(tempDir.resolve(name).resolve("reports-root"));
        Path marker = staleBuildStartMarker(buildDir);
        for (Map.Entry<String, String> file : buildFiles.entrySet()) {
            Path target = buildDir.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            writeString(target, file.getValue());
        }

        String fullScript = verifyScript(service, exercise);
        String collectSnippet = slice(fullScript, "rm -rf \"$REPORTS_DIR\"", "echo \"" + SandboxBuildCommandService.COLLECTED_MARKER);
        // Bind the variables the collect snippet reads; point REPORTS_DIR at a per-run dir so we can read back what was collected.
        Path reportsDir = reportsParent.resolve("solution");
        String script = "BUILD_DIR='" + buildDir + "'\nBUILD_START_MARKER='" + marker + "'\nREPORTS_DIR='" + reportsDir + "'\nrc=0\nseq=0\n" + collectSnippet + "\n";
        Path scriptFile = tempDir.resolve(name + "-collect.sh");
        writeString(scriptFile, script);
        runSh(scriptFile);

        Map<String, String> collected = new LinkedHashMap<>();
        if (Files.isDirectory(reportsDir)) {
            try (Stream<Path> files = Files.list(reportsDir)) {
                for (Path file : (Iterable<Path>) files.sorted()::iterator) {
                    collected.put(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        }
        return collected;
    }
}
