package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;

/**
 * Deterministic unit test for the generated {@code verify.sh}: it must embed the exercise's real build phases with the CI placeholders substituted to the hermetic layout, and
 * collect the build-fresh test/SCA reports into the verifier-owned dir from both the phase result paths and the common per-language locations. The OS-gated nested classes drive
 * the live collect step under a real {@code sh} and feed the collected report into the production {@code TestResultXmlParser}, proving collection covers exactly what the verifier
 * parses.
 */
class SandboxBuildCommandServiceTest {

    private static SandboxBuildCommandService factoryWithPhases(List<BuildPhaseDTO> phases) {
        BuildPhasesTemplateService phasesService = mock(BuildPhasesTemplateService.class);
        when(phasesService.getDefaultBuildPlanPhasesFor(any())).thenReturn(phases);
        return new SandboxBuildCommandService(Optional.of(phasesService), Optional.of(new BuildScriptProviderService()));
    }

    @Test
    void verifyScript_substitutesCiPlaceholders_andSearchesPhaseResultPaths() {
        // A Python-like phase that cd's into the (placeholder) test working directory and writes its report under test-reports/.
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "cd ${testWorkingDirectory}\npytest --junitxml=test-reports/results.xml", null, false,
                List.of("test-reports/*results.xml"));
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(new ProgrammingExercise());

        // The PHASE form of ${testWorkingDirectory} is substituted to "." (the seeded-harness sed stanza keeps the literal placeholder — a separate mechanism — so assert only the
        // phase).
        assertThat(script).doesNotContain("cd ${testWorkingDirectory}").contains("cd .");
        assertThat(script).contains("test-reports");
        // Only the non-authoritative liveness line is printed; no stdout-scraped verdict markers.
        assertThat(script).contains(SandboxBuildCommandService.COLLECTED_MARKER + " tests=$collected_tests").doesNotContain("HYPERION_RESULT").doesNotContain("HYPERION_TESTNAME");
    }

    @Test
    void verifyScript_collectsReportsIntoTheVerifierOwnedDir_regularFilesOnly_mtimeGated() {
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "echo run", null, false, List.of());
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(new ProgrammingExercise());
        // Per-assignment subdir of the verifier-owned reports dir, re-seeded empty each run.
        assertThat(script).contains("REPORTS_DIR=\"" + SandboxBuildCommandService.REPORTS_DIR + "/$ASSIGNMENT\"").contains("rm -rf \"$REPORTS_DIR\"")
                .contains("mkdir -p \"$REPORTS_DIR\"");
        // Only build-fresh regular files (find -type f, -newer marker excludes planted stale reports), renamed to <seq>__<canonical> with the JUnit token.
        assertThat(script).contains("find \"$BUILD_DIR\" -type f -newer \"$BUILD_START_MARKER\"").contains("cp -P")
                .contains(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + "$canonical").contains(SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN);
    }

    @Test
    void verifyScript_materializesTestsInSubdir_forLanguagesThatCheckOutTestsThere() {
        ProgrammingExercise go = new ProgrammingExercise();
        go.setProgrammingLanguage(ProgrammingLanguage.GO);
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "cd ${testWorkingDirectory}\ngo test ./...", null, false, List.of("${testWorkingDirectory}/test-results.xml"));
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(go);

        assertThat(script).contains("TEST_DEST=\"$BUILD_DIR/tests\"");
        assertThat(script).doesNotContain("cd ${testWorkingDirectory}").contains("cd tests");
    }

    @Test
    void verifyScript_fallsBackToConventionalBuild_whenNoPhases() {
        SandboxBuildCommandService factory = new SandboxBuildCommandService(Optional.empty(), Optional.empty());
        String script = factory.verifyScriptContent(new ProgrammingExercise());
        assertThat(script).contains("mvn clean test").contains("surefire-reports").contains("test-reports");
    }

    @Test
    void pristineBuildCommands_targetTheVerifierOwnedScript() {
        SandboxBuildCommandService factory = new SandboxBuildCommandService(Optional.empty(), Optional.empty());
        // The verifier runs the PRISTINE copy outside /workspace (unreachable by the agent's tools).
        assertThat(factory.pristineSolutionBuildCommand()).isEqualTo("sh /opt/hyperion/verify.sh solution");
        assertThat(factory.pristineTemplateBuildCommand()).isEqualTo("sh /opt/hyperion/verify.sh template");
        assertThat(SandboxBuildCommandService.reportsDirectoryFor("solution")).isEqualTo("/opt/hyperion/reports/solution");
    }

    @Test
    void verifyScript_doesNotSubstituteCiPlaceholders_soARawOneFailsTheBuildInsteadOfShippingToRealCi() {
        // The script used to sed the checkout placeholders into the copied harness. That HID a raw ${...}: the build passed in the sandbox and the same raw string reached real CI,
        // where it expands to an empty string (`rm -rf ${solutionWorkingDirectory}` -> `rm -rf `). Exercise creation already resolves the seeded repositories, so a raw placeholder
        // can only be something the agent wrote; letting the build fail is the point.
        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
        assertThat(script).doesNotContain("s#${studentParentWorkingDirectoryName}#").doesNotContain("s#${solutionWorkingDirectory}#")
                .doesNotContain("s#${studentWorkingDirectory}#").doesNotContain("s#${testWorkingDirectory}#");
    }

    @Test
    void verifyScript_materializesSiblingSolution_andSubstitutesRealLayout_forSolutionCheckoutLanguages() {
        // Haskell's harness references ${solutionWorkingDirectory}. When checkoutSolutionRepository is set, real CI materializes a sibling solution/ and substitutes the
        // placeholder
        // to solution/ (not the collapsed assignment), so the template run compares against the REAL solution; a collapse would make the template trivially pass -> false reject.
        ProgrammingExercise haskell = new ProgrammingExercise();
        haskell.setProgrammingLanguage(ProgrammingLanguage.HASKELL);
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setCheckoutSolutionRepository(true);
        haskell.setBuildConfig(buildConfig);

        String script = factoryWithPhases(List.of(new BuildPhaseDTO("test", "./run.sh -s", null, false, List.of("test-reports/results.xml")))).verifyScriptContent(haskell);

        // A sibling solution/ is materialized at the real CI checkout path, so the harness's reference to it resolves.
        assertThat(script).contains("mkdir -p \"$BUILD_DIR/solution\"").contains("cp -a \"$WORKSPACE/solution/.\" \"$BUILD_DIR/solution\"/");

    }

    @Test
    void verifyScript_doesNotMaterializeSolution_forLanguagesThatDoNotCheckItOut_soTheDifferentialIsUnchanged() {
        // Languages other than Haskell/OCaml check out no sibling solution/ (the enum throws -> solutionDir empty), so none is materialized and a broad-glob build cannot pick up
        // solution sources and inflate the differential.
        ProgrammingExercise go = new ProgrammingExercise();
        go.setProgrammingLanguage(ProgrammingLanguage.GO);
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setCheckoutSolutionRepository(true); // even when requested, Go has no solution checkout path, so none is materialized
        go.setBuildConfig(buildConfig);

        String script = factoryWithPhases(List.of(new BuildPhaseDTO("test", "go test ./...", null, false, List.of()))).verifyScriptContent(go);

        assertThat(script).doesNotContain("cp -a \"$WORKSPACE/solution/.").doesNotContain("mkdir -p \"$BUILD_DIR/solution\"");
    }
}
