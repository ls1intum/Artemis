package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

/** Protects student diagnostics from overly broad infrastructure-log filters. */
class BuildLogClassificationTest {

    private final BuildLogEntryService service = new BuildLogEntryService(null, null, null, null, null, null);

    @ParameterizedTest
    @MethodSource("dockerProgressLines")
    void hidesExactDockerProgressButKeepsSimilarStudentMessages(String progress, String diagnostic) {
        assertThat(service.isUnnecessaryBuildLogForProgrammingLanguage(progress, ProgrammingLanguage.PYTHON)).isTrue();
        assertThat(service.isUnnecessaryBuildLogForProgrammingLanguage(diagnostic, ProgrammingLanguage.PYTHON)).isFalse();
    }

    private static Stream<Arguments> dockerProgressLines() {
        return Stream.of(Arguments.of("Unable to find image 'test' locally", "Unable to find image 'test' in exercise"),
                Arguments.of("Digest: sha256:" + "a".repeat(64), "Digest: sha256:invalid"), Arguments.of("123456789012: Pulling fs layer", "student: Pulling fs layer"),
                Arguments.of("123456789012: Waiting", "student: Waiting"), Arguments.of("123456789012: Verifying Checksum", "student: Verifying Checksum"),
                Arguments.of("123456789012: Download complete", "student: Download complete"), Arguments.of("123456789012: Pull complete", "student: Pull complete"),
                Arguments.of("Jenkins seems to be running inside container abc", "Jenkins test assertion failed"),
                Arguments.of("Jenkins does not seem to be running inside a container", "Jenkins container assertion failed"), Arguments.of(".", ".."));
    }

    @ParameterizedTest
    @ValueSource(strings = { "Digest: example", "Status: example", "https://github.com/example/project", "image: Already exists", "image: Pulling", "image: Waiting",
            "image: Verifying", "image: Download complete", "Unable to find image" })
    void cAndSwiftSuppressImageSetupWithoutApplyingThoseRulesToPython(String line) {
        assertThat(service.isUnnecessaryBuildLogForProgrammingLanguage(line, ProgrammingLanguage.C)).isTrue();
        assertThat(service.isUnnecessaryBuildLogForProgrammingLanguage(line, ProgrammingLanguage.SWIFT)).isTrue();
        assertThat(service.isUnnecessaryBuildLogForProgrammingLanguage(line, ProgrammingLanguage.PYTHON)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "An illegal reflective access operation has occurred", "Illegal reflective access by example",
            "Please consider reporting this to the maintainers of example", "Use --illegal-access=warn to enable warnings of further illegal reflective access operations",
            "All illegal access operations will be denied in a future release" })
    void reflectiveWarningsRequireBothWarningPrefixAndRecognizedMessage(String message) {
        assertThat(service.isIllegalReflectionLog("WARNING: " + message)).isTrue();
        assertThat(service.isIllegalReflectionLog("Student output: " + message)).isFalse();
        assertThat(service.isIllegalReflectionLog("WARNING: your solution returned an incorrect result")).isFalse();
    }

    @Test
    void retainsErrorInfoAndCompilationDiagnosticAndStopsAtCombinedFailureSummary() {
        var time = ZonedDateTime.parse("2026-01-01T00:00:00Z");
        var lines = List.of(new BuildLogEntry(time, "[INFO] harmless", null), new BuildLogEntry(time, "[INFO] error: expected 5 but got 4", null),
                new BuildLogEntry(time, "Main.java:12: cannot find symbol", null), new BuildLogEntry(time, "COMPILATION ERROR BUILD FAILURE", null),
                new BuildLogEntry(time, "internal stack trace", null));

        var filtered = service.removeUnnecessaryLogsForProgrammingLanguage(lines, ProgrammingLanguage.JAVA);

        assertThat(filtered).extracting(BuildLogEntry::getLog).containsExactly("[INFO] error: expected 5 but got 4", "Main.java:12: cannot find symbol");
        assertThat(lines).hasSize(5);
        assertThat(filtered.getFirst()).isNotSameAs(lines.get(1));
        assertThat(filtered).allSatisfy(entry -> assertThat(entry.getTime()).isEqualTo(time));
    }

    @Test
    void allowsSeparatedBlankLinesAndPreservesRepeatedCCompilerOutput() {
        var logs = List.of(new BuildLogEntry(null, "", null), new BuildLogEntry(null, "diagnostic", null));
        assertThat(service.checkIfBuildLogIsNotADuplicate(ProgrammingLanguage.SWIFT, logs, "")).isTrue();
        assertThat(service.checkIfBuildLogIsNotADuplicate(ProgrammingLanguage.SWIFT, logs, "diagnostic")).isFalse();
        assertThat(service.checkIfBuildLogIsNotADuplicate(ProgrammingLanguage.C, logs, "diagnostic")).isTrue();
        assertThat(service.checkIfBuildLogIsNotADuplicate(ProgrammingLanguage.SWIFT, List.of(new BuildLogEntry(null, "", null)), "")).isFalse();
        assertThat(service.checkIfBuildLogIsNotADuplicate(ProgrammingLanguage.SWIFT, List.of(), "diagnostic")).isTrue();
    }
}
