package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;

class LocalCIBuildConfigurationServiceTest {

    private final LocalCIBuildConfigurationService localCIBuildConfigurationService = new LocalCIBuildConfigurationService(new BuildScriptProviderService());

    // Matches LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir", which the generated script always `cd`s
    // into unconditionally - there is no way to override that path from the phase/build-config input, so the
    // execution-based test below has to create it for real rather than working in an arbitrary temp directory.
    private static final Path TESTING_DIR = Path.of("/var/tmp/testing-dir");

    @AfterEach
    void cleanUp() throws IOException {
        if (Files.exists(TESTING_DIR)) {
            Files.walk(TESTING_DIR).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void createBuildScriptFromActivePhases_runsEveryPhaseEvenIfAnEarlierOneFails() throws IOException, InterruptedException {
        Files.createDirectories(TESTING_DIR);

        List<BuildPhaseDTO> phases = List.of(new BuildPhaseDTO("compile", "echo compile_ran", null, false, List.of()),
                // A test task exiting non-zero because tests failed is a completely normal outcome, not a broken
                // script - later phases (structural_tests's own result XML, and behavior_tests entirely) must still
                // run, exactly like the milestone/user-story build that first surfaced this bug.
                new BuildPhaseDTO("structural_tests", "echo structural_tests_ran; exit 1", null, false, List.of()),
                new BuildPhaseDTO("behavior_tests", "echo behavior_tests_ran", null, false, List.of()));

        String script = localCIBuildConfigurationService.createBuildScriptFromActivePhases(new ProgrammingExerciseBuildConfig(), phases);

        Path scriptFile = TESTING_DIR.resolve("script.sh");
        Files.writeString(scriptFile, script);

        Process process = new ProcessBuilder("bash", scriptFile.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(output).contains("compile_ran", "structural_tests_ran", "behavior_tests_ran");
        // The script's own exit code still reflects that something failed - just no longer at the cost of skipping
        // every phase after it.
        assertThat(process.exitValue()).isNotZero();
    }

    @Test
    void createBuildScriptFromActivePhases_guardsEveryNonForceRunPhaseInvocation() {
        List<BuildPhaseDTO> phases = List.of(new BuildPhaseDTO("compile", "echo hi", null, false, List.of()),
                new BuildPhaseDTO("structural_tests", "echo hi", null, false, List.of()), new BuildPhaseDTO("behavior_tests", "echo hi", null, false, List.of()));

        String script = localCIBuildConfigurationService.createBuildScriptFromActivePhases(new ProgrammingExerciseBuildConfig(), phases);

        // Every phase invocation in the main() loop must be guarded so a non-zero exit doesn't abort the loop -
        // asserted by count rather than by matching the exact generated line, so this doesn't need updating for
        // unrelated formatting changes to the surrounding script.
        assertThat(script.lines().filter(line -> line.contains("script_sourcing;")).count()).isEqualTo(3);
        assertThat(script.lines().filter(line -> line.contains("script_sourcing;") && line.contains("|| phase_exit_code=$?")).count()).isEqualTo(3);
    }
}
