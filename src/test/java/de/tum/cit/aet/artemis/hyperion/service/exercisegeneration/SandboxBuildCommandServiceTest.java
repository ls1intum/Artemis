package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser;
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
    void verifyScript_substitutesCiPlaceholdersInsideTheCopiedTestHarness_soTheAgentNeverNeedsToEditIt() {
        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
        assertThat(script).contains("s#${studentParentWorkingDirectoryName}#assignment#g").contains("s#${solutionWorkingDirectory}#assignment#g")
                .contains("s#${studentWorkingDirectory}#/assignment/src#g").contains("s#${testWorkingDirectory}#.#g");
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

        // A sibling solution/ is materialized and the placeholders resolve to the real CI layout: solution/ (not the collapsed assignment), test root.
        assertThat(script).contains("mkdir -p \"$BUILD_DIR/solution\"").contains("cp -a \"$WORKSPACE/solution/.\" \"$BUILD_DIR/solution\"/");
        assertThat(script).contains("s#${solutionWorkingDirectory}#solution#g").contains("s#${studentParentWorkingDirectoryName}#assignment#g")
                .contains("s#${testWorkingDirectory}#.#g");
    }

    @Test
    void verifyScript_doesNotMaterializeSolution_forLanguagesThatDoNotCheckItOut_soTheDifferentialIsUnchanged() {
        // Languages other than Haskell/OCaml check out no sibling solution/ (the enum throws -> solutionDir empty), so none is materialized and a broad-glob build cannot pick up
        // solution sources and inflate the differential. The placeholder never appears in their harness, so its no-op substitution stays at the collapse value.
        ProgrammingExercise go = new ProgrammingExercise();
        go.setProgrammingLanguage(ProgrammingLanguage.GO);
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setCheckoutSolutionRepository(true); // even when requested, Go has no solution checkout path, so none is materialized
        go.setBuildConfig(buildConfig);

        String script = factoryWithPhases(List.of(new BuildPhaseDTO("test", "go test ./...", null, false, List.of()))).verifyScriptContent(go);

        assertThat(script).doesNotContain("cp -a \"$WORKSPACE/solution/.").doesNotContain("mkdir -p \"$BUILD_DIR/solution\"");
        assertThat(script).contains("s#${solutionWorkingDirectory}#assignment#g");
    }

    /**
     * Drives the live placeholder-substitution stanza against a fixture Haskell {@code test.cabal} under a real {@code sh}, confirming raw {@code ${...}} placeholders resolve to
     * the assignment/ layout.
     */
    @Nested
    @EnabledOnOs({ LINUX, MAC })
    class HarnessPlaceholderSubstitution {

        @Test
        void substitutesSeededCabalPlaceholders_toTheAssignmentLayout(@TempDir Path tempDir) throws Exception {
            Path testDest = Files.createDirectories(tempDir.resolve("tests"));
            String seededCabal = """
                    library submission
                      hs-source-dirs: ${studentParentWorkingDirectoryName}/src
                    library solution
                      hs-source-dirs: ${solutionWorkingDirectory}/src
                    """;
            VerifyScriptTestHarness.writeString(testDest.resolve("test.cabal"), seededCabal);

            String stanza = substitutionStanza();
            String script = "TEST_DEST='" + testDest + "'\n" + stanza + "\n";
            Path scriptFile = tempDir.resolve("subst.sh");
            VerifyScriptTestHarness.writeString(scriptFile, script);
            Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("substitution stanza did not finish in time");
            }
            String produced = Files.readString(testDest.resolve("test.cabal"), StandardCharsets.UTF_8);
            assertThat(produced).doesNotContain("${").contains("hs-source-dirs: assignment/src");
            assertThat(produced.split("hs-source-dirs: assignment/src", -1)).hasSizeGreaterThanOrEqualTo(3);
        }

        private String substitutionStanza() {
            String fullScript = VerifyScriptTestHarness.verifyScript();
            return VerifyScriptTestHarness.slice(fullScript, "find \"$TEST_DEST\" -type f", "done");
        }
    }

    /**
     * Runs the live collect step against fixture JUnit XML under a real {@code sh} and feeds the result into the production {@code TestResultXmlParser}, proving the script
     * collects
     * exactly what the verifier parses and that the planted-report mitigation ({@code -newer} mtime gate) holds.
     */
    @Nested
    @EnabledOnOs({ LINUX, MAC })
    class ReportCollection {

        private SandboxBuildCommandService factory() {
            BuildPhasesTemplateService phasesService = mock(BuildPhasesTemplateService.class);
            when(phasesService.getDefaultBuildPlanPhasesFor(any())).thenReturn(List.of(new BuildPhaseDTO("test", "echo run", null, false, List.of())));
            return new SandboxBuildCommandService(Optional.of(phasesService), Optional.of(new BuildScriptProviderService()));
        }

        private static final String SUREFIRE = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="StackTest" tests="3" failures="1" errors="0" skipped="0">
                  <testcase name="stack_initially_empty" classname="StackTest"/>
                  <testcase name="push_then_pop" classname="StackTest"/>
                  <testcase name="size_tracks_elements" classname="StackTest"><failure message="x"/></testcase>
                </testsuite>
                """;

        @Test
        void collectsTheJUnitReport_andProductionParserSeesTheRightTests(@TempDir Path tempDir) throws Exception {
            Map<String, String> collected = VerifyScriptTestHarness.collect(factory(), new ProgrammingExercise(), tempDir, "junit", Map.of("test-results/results.xml", SUREFIRE));
            assertThat(collected).hasSize(1);
            String collectedXml = collected.values().iterator().next();
            assertThat(collected.keySet().iterator().next()).endsWith(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN);

            // The production parser sees exactly the two passing + one failing testcase.
            List<LocalCITestJobDTO> failed = new ArrayList<>();
            List<LocalCITestJobDTO> ok = new ArrayList<>();
            TestResultXmlParser.processTestResultFile(collectedXml, failed, ok);
            assertThat(ok.stream().map(LocalCITestJobDTO::name)).containsExactlyInAnyOrder("stack_initially_empty", "push_then_pop");
            assertThat(failed.stream().map(LocalCITestJobDTO::name)).containsExactly("size_tracks_elements");
        }

        @Test
        void doesNotCollectAReportPlantedBeforeTheBuildStart(@TempDir Path tempDir) throws Exception {
            // A report whose mtime predates the build-start marker must NOT be collected (anti-forgery): pre-write it and back-date it.
            Path buildDir = Files.createDirectories(tempDir.resolve("planted").resolve("build"));
            Path reportsDir = tempDir.resolve("planted").resolve("reports-root").resolve("solution");
            Path marker = buildDir.resolve(".hyperion-build-start");
            VerifyScriptTestHarness.writeString(marker, "");
            Path reportFile = buildDir.resolve("surefire-reports").resolve("planted.xml");
            VerifyScriptTestHarness.writeString(reportFile, SUREFIRE);
            Files.setLastModifiedTime(reportFile, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(7200))); // older than the marker
            Files.setLastModifiedTime(marker, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(3600)));

            String fullScript = factory().verifyScriptContent(new ProgrammingExercise());
            String collectSnippet = VerifyScriptTestHarness.slice(fullScript, "rm -rf \"$REPORTS_DIR\"", "echo \"" + SandboxBuildCommandService.COLLECTED_MARKER);
            String script = "BUILD_DIR='" + buildDir + "'\nBUILD_START_MARKER='" + marker + "'\nREPORTS_DIR='" + reportsDir + "'\nrc=0\nseq=0\n" + collectSnippet + "\n";
            Path scriptFile = tempDir.resolve("planted-collect.sh");
            VerifyScriptTestHarness.writeString(scriptFile, script);
            VerifyScriptTestHarness.runSh(scriptFile);

            assertThat(Files.isDirectory(reportsDir) ? Files.list(reportsDir).count() : 0L).as("a stale planted report is not collected").isZero();
        }
    }

    /**
     * The static-code-analysis collection. SCA disabled: no SCA reports collected; SCA enabled: each tool's canonical report collected by name (keeping the name so the verifier's
     * production {@code ReportParser} routes it). The live tests slice the collect block out of the generated script and run it under a real {@code sh}.
     */
    @Nested
    class StaticCodeAnalysisCollection {

        private SandboxBuildCommandService factory() {
            BuildPhasesTemplateService phasesService = mock(BuildPhasesTemplateService.class);
            when(phasesService.getDefaultBuildPlanPhasesFor(any())).thenReturn(List.of(new BuildPhaseDTO("build", "echo build", null, false, List.of())));
            return new SandboxBuildCommandService(Optional.of(phasesService), Optional.of(new BuildScriptProviderService()));
        }

        private ProgrammingExercise exercise(ProgrammingLanguage language, boolean scaEnabled) {
            ProgrammingExercise exercise = new ProgrammingExercise();
            exercise.setProgrammingLanguage(language);
            exercise.setStaticCodeAnalysisEnabled(scaEnabled);
            return exercise;
        }

        @Test
        void scaDisabled_collectsNoScaReports() {
            String script = factory().verifyScriptContent(exercise(ProgrammingLanguage.JAVA, false));
            assertThat(script).doesNotContain("spotbugsXml.xml").doesNotContain("collected_sca=$((collected_sca + 1))");
        }

        @Test
        void scaEnabled_collectsTheLanguageToolReports() {
            String script = factory().verifyScriptContent(exercise(ProgrammingLanguage.JAVA, true));
            // SpotBugs/Checkstyle/PMD/CPD by canonical name, build-fresh only.
            assertThat(script).contains("-name 'spotbugsXml.xml'").contains("-name 'checkstyle-result.xml'").contains("-name 'pmd.xml'").contains("-name 'cpd.xml'")
                    .contains("-newer \"$BUILD_START_MARKER\"").contains("collected_sca=$((collected_sca + 1))");
        }

        @Test
        void scaEnabled_python_collectsRuffSarif() {
            String script = factory().verifyScriptContent(exercise(ProgrammingLanguage.PYTHON, true));
            assertThat(script).contains("-name 'ruff.sarif'");
        }

        @EnabledOnOs({ LINUX, MAC })
        @Test
        void scriptIsValidPosixShell_forEveryScaCapableLanguage(@TempDir Path tempDir) throws Exception {
            for (ProgrammingLanguage language : List.of(ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.DART, ProgrammingLanguage.JAVA,
                    ProgrammingLanguage.JAVASCRIPT, ProgrammingLanguage.PYTHON, ProgrammingLanguage.R, ProgrammingLanguage.RUBY, ProgrammingLanguage.RUST,
                    ProgrammingLanguage.TYPESCRIPT)) {
                String fullScript = factory().verifyScriptContent(exercise(language, true));
                Path scriptFile = tempDir.resolve("full-" + language.name().toLowerCase() + ".sh");
                VerifyScriptTestHarness.writeString(scriptFile, fullScript);
                Process process = new ProcessBuilder("sh", "-n", scriptFile.toString()).redirectErrorStream(true).start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IllegalStateException("sh -n did not finish in time for " + language);
                }
                assertThat(process.exitValue()).as("the generated script for %s is valid POSIX sh (sh -n: %s)", language, output).isZero();
            }
        }

        @EnabledOnOs({ LINUX, MAC })
        @Test
        void liveCollect_collectsSpotbugsAndCheckstyleReports_forProductionParsing(@TempDir Path tempDir) throws Exception {
            Map<String, String> collected = VerifyScriptTestHarness.collect(factory(), exercise(ProgrammingLanguage.JAVA, true), tempDir, "java-sca",
                    Map.of("test-results/results.xml", "<testsuite name=\"T\" tests=\"0\"/>", "spotbugsXml.xml", """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <BugCollection version="4.7.3"><BugInstance type="DM_DEFAULT_ENCODING" category="STYLE"><SourceLine start="12" end="12"/></BugInstance></BugCollection>
                            """, "checkstyle-result.xml", """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <checkstyle version="10.3"><file name="Stack.java">
                              <error line="3" severity="warning" message="x" source="com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocTypeCheck"/></file></checkstyle>
                            """));
            // Both SCA reports are collected under their canonical names so the verifier's production ReportParser can route them.
            assertThat(collected.keySet()).anyMatch(n -> n.endsWith("spotbugsXml.xml")).anyMatch(n -> n.endsWith("checkstyle-result.xml"));
        }
    }
}
