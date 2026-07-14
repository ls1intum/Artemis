package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

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

final class VerifyScriptTestHarness {

    private VerifyScriptTestHarness() {
    }

    static void writeString(Path path, CharSequence content) throws IOException {
        FileUtils.writeStringToFile(path.toFile(), content.toString(), StandardCharsets.UTF_8);
    }

    static String verifyScript() {
        return new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
    }

    static String verifyScript(SandboxBuildCommandService service, ProgrammingExercise exercise) {
        return service.verifyScriptContent(exercise);
    }

    static String slice(String script, String startMarker, String endMarker) {
        int start = script.indexOf(startMarker);
        assertThat(start).as("snippet start marker '%s' present in verify.sh", startMarker).isNotNegative();
        int end = script.indexOf(endMarker, start);
        assertThat(end).as("snippet end marker '%s' present in verify.sh after the start", endMarker).isGreaterThan(start);
        return script.substring(start, end + endMarker.length());
    }

    static String runSh(Path scriptFile) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("sh").redirectInput(scriptFile.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("verify.sh snippet did not finish in time");
        }
        return output;
    }

    static Path staleBuildStartMarker(Path buildDir) throws IOException {
        Path marker = buildDir.resolve(".hyperion-build-start");
        writeString(marker, "");
        Files.setLastModifiedTime(marker, FileTime.from(Instant.now().minusSeconds(3600)));
        return marker;
    }

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
