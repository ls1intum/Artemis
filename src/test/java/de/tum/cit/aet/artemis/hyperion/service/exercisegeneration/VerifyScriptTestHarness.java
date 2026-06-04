package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Shared test harness for driving the EXACT shell snippets the shipped {@code verify.sh} contains: it renders the live script via {@link SandboxBuildCommandService}, slices a
 * named
 * region out of it (so the shell under test is byte-identical to what ships), and runs that region under a real POSIX {@code sh}. Used by both
 * {@link SandboxBuildCommandServiceTest}
 * and {@link SandboxProductionParityDivergenceTest} so the aggregation/emit drivers live in one place rather than being copy-pasted across the two suites.
 */
final class VerifyScriptTestHarness {

    private VerifyScriptTestHarness() {
    }

    /** The four aggregated counters {@code verify.sh} computes from the JUnit XML. */
    record Aggregate(int tests, int failures, int errors, int skipped) {
    }

    /** The {@code HYPERION_TESTNAME} and {@code HYPERION_TESTFAIL} names the {@code emit_test_lines} block printed for a fixture. */
    record Emitted(List<String> names, List<String> failed) {
    }

    /** Writes a UTF-8 text fixture via Apache {@link FileUtils} (the arch-mandated replacement for {@code Files.write*}), creating any missing parent directories. */
    static void writeString(Path path, CharSequence content) throws IOException {
        FileUtils.writeStringToFile(path.toFile(), content.toString(), StandardCharsets.UTF_8);
    }

    /** The full text of the live generated {@code verify.sh} for a default (phase-less) exercise, the source of every sliced snippet below. */
    static String verifyScript() {
        return new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
    }

    /**
     * Slices the half-open region {@code [start, endInclusive)} out of the live {@code verify.sh}, where the region runs from the FIRST occurrence of {@code startMarker} through
     * the
     * end of the first occurrence of {@code endMarker} at or after it. Asserts both markers are present so a drifting snippet boundary fails loudly.
     *
     * @param startMarker the literal text the snippet starts at
     * @param endMarker   the literal text the snippet ends at (included in full)
     * @return the sliced shell snippet
     */
    static String slice(String startMarker, String endMarker) {
        String script = verifyScript();
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

    /**
     * Writes the fixture XML into a fresh report directory (with an OLD build-start marker so the report is strictly newer and counted) and runs the live aggregation snippet
     * against
     * it under {@code sh}, returning the four counters the script would print.
     *
     * @param tempDir   the per-test temporary directory
     * @param name      a unique name for this run's build subtree
     * @param reportXml the JUnit XML fixture to aggregate
     * @return the aggregated counters
     */
    static Aggregate aggregate(Path tempDir, String name, String reportXml) throws IOException, InterruptedException {
        Path buildDir = Files.createDirectories(tempDir.resolve(name));
        Path marker = staleBuildStartMarker(buildDir);
        Path reportDir = Files.createDirectories(buildDir.resolve("test-results"));
        writeString(reportDir.resolve("results.xml"), reportXml);

        String script = "BUILD_DIR='" + buildDir + "'\nBUILD_START_MARKER='" + marker + "'\n" + aggregationSnippet()
                + "\necho \"tests=$tests failures=$failures errors=$errors skipped=$skipped\"\n";
        Path scriptFile = tempDir.resolve(name + "-aggregate.sh");
        writeString(scriptFile, script);

        return parseAggregate(runSh(scriptFile));
    }

    /**
     * Writes the fixture XML into a fresh report directory and runs the live {@code emit_test_lines} block against it under {@code sh}, returning the emitted test-name and
     * failing-test-name lines (with the given {@code MARK_SUFFIX}, modelling the per-run nonce).
     *
     * @param tempDir    the per-test temporary directory
     * @param name       a unique name for this run's report subtree
     * @param reportXml  the JUnit XML fixture to emit names for
     * @param markSuffix the {@code MARK_SUFFIX} value the verifier would pass (empty for no nonce)
     * @return the emitted names and failing names
     */
    static Emitted emit(Path tempDir, String name, String reportXml, String markSuffix) throws IOException, InterruptedException {
        Path reportDir = Files.createDirectories(tempDir.resolve(name).resolve("test-results"));
        Path report = reportDir.resolve("results.xml");
        writeString(report, reportXml);

        String script = "MARK_SUFFIX='" + markSuffix + "'\nxml='" + report + "'\n" + emitSnippet() + "\n";
        Path scriptFile = tempDir.resolve(name + "-emit.sh");
        writeString(scriptFile, script);

        String output = runSh(scriptFile);
        List<String> names = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String line : output.split("\n")) {
            if (line.startsWith("HYPERION_TESTNAME ")) {
                names.add(line.substring("HYPERION_TESTNAME ".length()));
            }
            else if (line.startsWith("HYPERION_TESTFAIL ")) {
                failed.add(line.substring("HYPERION_TESTFAIL ".length()));
            }
        }
        return new Emitted(names, failed);
    }

    /** Creates the {@code .hyperion-build-start} marker with an mtime an hour in the past, so any report written afterwards is strictly newer and is counted. */
    static Path staleBuildStartMarker(Path buildDir) throws IOException {
        Path marker = buildDir.resolve(".hyperion-build-start");
        writeString(marker, "");
        Files.setLastModifiedTime(marker, FileTime.from(Instant.now().minusSeconds(3600)));
        return marker;
    }

    /**
     * Slices the report-aggregation block (the skipped-aware {@code tests} computation through the last {@code errors=$(sum_attr errors)} counter) out of the live
     * {@code verify.sh}.
     */
    static String aggregationSnippet() {
        return slice("xml=$(find", "errors=$(sum_attr errors)");
    }

    /** Slices the {@code emit_test_lines()} function definition plus its standalone invocation out of the live {@code verify.sh}. */
    static String emitSnippet() {
        return slice("emit_test_lines() {", "\nemit_test_lines\n");
    }

    /** Parses a {@code tests=N failures=N errors=N skipped=N} line (in any token order) into an {@link Aggregate}. */
    static Aggregate parseAggregate(String output) {
        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        for (String token : output.trim().split("\\s+")) {
            String[] keyValue = token.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            switch (keyValue[0]) {
                case "tests" -> tests = Integer.parseInt(keyValue[1]);
                case "failures" -> failures = Integer.parseInt(keyValue[1]);
                case "errors" -> errors = Integer.parseInt(keyValue[1]);
                case "skipped" -> skipped = Integer.parseInt(keyValue[1]);
                default -> {
                }
            }
        }
        return new Aggregate(tests, failures, errors, skipped);
    }
}
