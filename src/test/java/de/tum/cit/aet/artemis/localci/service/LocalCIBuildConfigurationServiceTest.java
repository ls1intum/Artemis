package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;

/**
 * Unit tests for the shell script every local build runs.
 * <p>
 * The script is assembled from the phases an instructor configured, and its shape carries the semantics: {@code set -e}
 * makes a failing phase stop the build, each phase runs from the directory the build started in rather than wherever the
 * previous one left off, and a phase marked as force-run has to execute even when an earlier one failed, which is what
 * the {@code EXIT} trap is for. Getting any of that wrong produces builds that either report the wrong result or lose
 * the test results entirely.
 */
@ExtendWith(MockitoExtension.class)
class LocalCIBuildConfigurationServiceTest {

    @Mock
    private BuildScriptProviderService buildScriptProviderService;

    private LocalCIBuildConfigurationService localCIBuildConfigurationService;

    @BeforeEach
    void setUp() {
        localCIBuildConfigurationService = new LocalCIBuildConfigurationService(buildScriptProviderService);
        // The placeholders for the checkout directories are substituted by a collaborator; this test is about what the script is built from.
        when(buildScriptProviderService.replacePlaceholders(anyString(), any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static BuildPhaseDTO phase(String name, String script, boolean forceRun) {
        return new BuildPhaseDTO(name, script, BuildPhaseCondition.ALWAYS, forceRun, List.of());
    }

    private String buildScriptFor(BuildPhaseDTO... phases) {
        return localCIBuildConfigurationService.createBuildScriptFromActivePhases(new ProgrammingExerciseBuildConfig(), List.of(phases));
    }

    @Test
    void createBuildScript_startsFromTheTestingDirectoryAndStopsOnTheFirstFailure() {
        String script = buildScriptFor(phase("build", "./gradlew build", false));

        assertThat(script).as("the script is a bash script").startsWith("#!/usr/bin/env bash\n");
        // Without set -e a failing command would be ignored and the build would report success.
        assertThat(script).as("a failing command stops the build").contains("set -e\n");
        assertThat(script).as("the build starts in the directory the repositories were checked out into").contains("/testing-dir\n");
        assertThat(script).as("the starting directory is remembered so that every phase can return to it").contains("export INITIAL_WORKING_DIRECTORY=${PWD}");
        assertThat(script).as("the script runs its main function with the arguments it was given").endsWith("main \"${@}\"\n");
    }

    @Test
    void createBuildScript_wrapsEveryPhaseInAFunctionOfItsOwn() {
        String script = buildScriptFor(phase("build", "./gradlew build", false), phase("test", "./gradlew test", false));

        assertThat(script).as("each phase becomes a function named after it").contains("build () {").contains("test () {");
        assertThat(script).as("each phase announces itself in the build log").contains("echo '⚙️ executing build'").contains("echo '⚙️ executing test'");
        assertThat(script).as("the commands of a phase are part of its function").contains("./gradlew build").contains("./gradlew test");
    }

    @Test
    void createBuildScript_runsEveryPhaseFromTheDirectoryTheBuildStartedIn() {
        // A phase that ends in a subdirectory must not decide where the next one starts, otherwise adding a phase changes what the following ones do.
        String script = buildScriptFor(phase("build", "cd subproject && ./gradlew build", false), phase("test", "./gradlew test", false));

        assertThat(script).as("each phase returns to the starting directory first")
                .contains("  cd \"${INITIAL_WORKING_DIRECTORY}\"\n  bash -c \"source ${_script_name} script_sourcing; build\"\n"
                        + "  cd \"${INITIAL_WORKING_DIRECTORY}\"\n  bash -c \"source ${_script_name} script_sourcing; test\"\n");
    }

    @Test
    void createBuildScript_runsAForceRunPhaseEvenWhenAnEarlierPhaseFailed() {
        // Collecting the test results is the usual force-run phase: without it a failed build reports no results at all rather than the failures it found.
        String script = buildScriptFor(phase("test", "./gradlew test", false), phase("collectResults", "cp -r build/test-results .", true));

        assertThat(script).as("the force-run phases are collected into a phase that runs at the end").contains("final_force_run_post_action () {");
        assertThat(script).as("that phase is trapped on exit, so it also runs after a failure").contains("trap final_force_run_post_action EXIT");
        assertThat(script).as("errors no longer stop the build once the force-run phase is reached").contains("set +e");
        assertThat(script).as("the force-run phase is not also run as a normal phase").doesNotContain("bash -c \"source ${_script_name} script_sourcing; collectResults\"");
    }

    @Test
    void createBuildScript_withoutAForceRunPhase_installsNoTrap() {
        String script = buildScriptFor(phase("build", "./gradlew build", false));

        assertThat(script).as("nothing has to run after a failure, so no trap is installed").doesNotContain("trap").doesNotContain("final_force_run_post_action");
    }

    @Test
    void createBuildScript_canBeSourcedWithoutRunningAnything() {
        // Every phase is executed by sourcing the script in a subshell, which must define the functions without executing the build again.
        String script = buildScriptFor(phase("build", "./gradlew build", false));

        assertThat(script).contains("  if [[ \"${1}\" == \"script_sourcing\" ]]; then\n    return 0");
    }

    @Test
    void createBuildScript_forAPhaseWithoutCommands_stillDefinesIt() {
        // An instructor can leave a phase empty while writing an exercise; the script still has to be valid bash.
        String script = buildScriptFor(phase("placeholder", "   ", false));

        assertThat(script).contains("placeholder () {").contains("echo '⚙️ executing placeholder'");
        assertThat(script).as("an empty phase contributes no commands").doesNotContain("   \n  \n");
    }

    @Test
    void createBuildScript_indentsTheCommandsOfAPhaseButKeepsBlankLines() {
        String script = buildScriptFor(phase("build", "echo one\n\necho two", false));

        assertThat(script).as("the commands are indented into the function, and a blank line stays blank rather than becoming whitespace")
                .contains("build () {\n  echo '⚙️ executing build'\n  echo one\n\n  echo two\n}");
    }

    @Test
    void createBuildScript_forAnExerciseWithoutAnyPhase_producesAScriptThatDoesNothing() {
        String script = buildScriptFor();

        assertThat(script).startsWith("#!/usr/bin/env bash\n").endsWith("main \"${@}\"\n");
        assertThat(script).as("there is nothing to run and nothing to trap").doesNotContain("trap");
    }
}
