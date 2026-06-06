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
 * Deterministic unit test for the generated {@code verify.sh}: it must embed the exercise's real build phases with the CI directory placeholders substituted to the hermetic
 * layout, and COLLECT the build-fresh test/SCA report files into the verifier-owned reports directory — from both the phase's declared result paths and the common per-language
 * locations, so non-Java languages (whose reports land in {@code test-reports/}, not {@code surefire-reports/}) are collected. The verdict is no longer parsed in the shell; the
 * script's job is to assemble the layout, run the phases, and collect the reports. The OS-gated nested classes drive the live collect step under a real {@code sh} and feed the
 * collected JUnit report into the production {@code TestResultXmlParser}, proving the collection covers exactly the reports the verifier then parses.
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

        // ${testWorkingDirectory} is substituted to the hermetic test root (".") in the build PHASE, not left as a literal that the shell would mis-expand. (The placeholder still
        // appears verbatim in the seeded-harness sed-substitution stanza, which is a separate, intentional mechanism — so we assert the PHASE form is gone, not the whole script.)
        assertThat(script).doesNotContain("cd ${testWorkingDirectory}").contains("cd .");
        // The phase's own report location and the common test-reports/ location are both searched, so pytest's JUnit XML is collected.
        assertThat(script).contains("test-reports");
        // The verdict is no longer scraped from stdout: the script prints only the non-authoritative liveness line and collects reports into the verifier-owned dir.
        assertThat(script).contains(SandboxBuildCommandService.COLLECTED_MARKER + " tests=$collected_tests").doesNotContain("HYPERION_RESULT").doesNotContain("HYPERION_TESTNAME");
    }

    @Test
    void verifyScript_collectsReportsIntoTheVerifierOwnedDir_regularFilesOnly_mtimeGated() {
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "echo run", null, false, List.of());
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(new ProgrammingExercise());
        // The reports are collected into a per-assignment subdir of the constant verifier-owned reports dir, re-seeded empty each run.
        assertThat(script).contains("REPORTS_DIR=\"" + SandboxBuildCommandService.REPORTS_DIR + "/$ASSIGNMENT\"").contains("rm -rf \"$REPORTS_DIR\"")
                .contains("mkdir -p \"$REPORTS_DIR\"");
        // Only build-fresh, regular files are collected: find -type f excludes symlinks/dirs, and -newer "$BUILD_START_MARKER" excludes planted stale reports.
        assertThat(script).contains("find \"$BUILD_DIR\" -type f -newer \"$BUILD_START_MARKER\"").contains("cp -P");
        // Each collected file is renamed to <seq>__<canonical>; JUnit reports carry the fixed JUnit token so the verifier routes them to TestResultXmlParser.
        assertThat(script).contains(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + "$canonical").contains(SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN);
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
        // The authoritative verifier runs the PRISTINE copy outside /workspace (unreachable by the agent's tools); no nonce is passed (the verdict rests on the collected reports).
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
        // Haskell's harness references ${solutionWorkingDirectory} (the cabal's `library solution`, the test mixin). Real CI checks out a sibling solution/ for the
        // template/submission build when checkoutSolutionRepository is set, and substitutes ${solutionWorkingDirectory}->solution (NOT the collapsed assignment). The sandbox must
        // reproduce that exactly, so the template run compares the template against the REAL solution rather than against itself (a collapse would make the template trivially pass
        // -> false reject).
        ProgrammingExercise haskell = new ProgrammingExercise();
        haskell.setProgrammingLanguage(ProgrammingLanguage.HASKELL);
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setCheckoutSolutionRepository(true);
        haskell.setBuildConfig(buildConfig);

        String script = factoryWithPhases(List.of(new BuildPhaseDTO("test", "./run.sh -s", null, false, List.of("test-reports/results.xml")))).verifyScriptContent(haskell);

        // A sibling solution/ is materialized from the seeded solution workspace, alongside assignment/.
        assertThat(script).contains("mkdir -p \"$BUILD_DIR/solution\"").contains("cp -a \"$WORKSPACE/solution/.\" \"$BUILD_DIR/solution\"/");
        // The harness placeholders resolve to the real CI layout: solution/ (not the collapsed assignment), test root for Haskell.
        assertThat(script).contains("s#${solutionWorkingDirectory}#solution#g").contains("s#${studentParentWorkingDirectoryName}#assignment#g")
                .contains("s#${testWorkingDirectory}#.#g");
    }

    @Test
    void verifyScript_doesNotMaterializeSolution_forLanguagesThatDoNotCheckItOut_soTheDifferentialIsUnchanged() {
        // Every language other than Haskell/OCaml checks out no sibling solution/ for the template/submission build (the enum throws -> solutionDir empty). The sandbox must NOT
        // create a solution/ for them, so a broad-glob build cannot pick up solution sources and inflate/short-circuit the differential; the ${solutionWorkingDirectory}
        // placeholder
        // never appears in their harness, so its (no-op) substitution stays at the historical collapse value.
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
     * Drives the EXACT placeholder-substitution stanza from the live {@code verify.sh} against a fixture Haskell {@code test.cabal} under a real POSIX {@code sh}, so the shell
     * that
     * ships is the shell under test. Confirms the seeded raw {@code ${...}} placeholders resolve to the sandbox {@code assignment/} layout.
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
     * Runs the live COLLECT step from the generated {@code verify.sh} against fixture JUnit XML under a real POSIX {@code sh} and feeds the collected report into the production
     * {@code TestResultXmlParser}, proving (a) the script collects exactly the reports the verifier parses, (b) the planted-report mitigation (pre-clean + {@code -newer} mtime
     * gate) still holds, and (c) a Catch2-style report's test-CASE count survives parity. Parity-by-construction means there is no separate shell parser to drift any more.
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
            // Exactly one JUnit report was collected, under the JUnit canonical token.
            assertThat(collected).hasSize(1);
            String collectedXml = collected.values().iterator().next();
            assertThat(collected.keySet().iterator().next()).endsWith(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR + SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN);

            // The production parser sees exactly the two passing + one failing testcase — the verdict source the verifier consumes.
            List<LocalCITestJobDTO> failed = new ArrayList<>();
            List<LocalCITestJobDTO> ok = new ArrayList<>();
            TestResultXmlParser.processTestResultFile(collectedXml, failed, ok);
            assertThat(ok.stream().map(LocalCITestJobDTO::name)).containsExactlyInAnyOrder("stack_initially_empty", "push_then_pop");
            assertThat(failed.stream().map(LocalCITestJobDTO::name)).containsExactly("size_tracks_elements");
        }

        @Test
        void doesNotCollectAReportPlantedBeforeTheBuildStart(@TempDir Path tempDir) throws Exception {
            // A planted report whose mtime predates the build-start marker must NOT be collected (anti-forgery). We simulate it by pre-writing the report and back-dating it.
            Path buildDir = Files.createDirectories(tempDir.resolve("planted").resolve("build"));
            Path reportsDir = tempDir.resolve("planted").resolve("reports-root").resolve("solution");
            Path marker = buildDir.resolve(".hyperion-build-start");
            VerifyScriptTestHarness.writeString(marker, "");
            Path reportFile = buildDir.resolve("surefire-reports").resolve("planted.xml");
            VerifyScriptTestHarness.writeString(reportFile, SUREFIRE);
            // Plant the report OLDER than the marker.
            Files.setLastModifiedTime(reportFile, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(7200)));
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
     * The static-code-analysis collection (Defect D2). When SCA is disabled the script must collect NO SCA reports (its behaviour for a non-SCA exercise is unchanged); when
     * enabled
     * it must collect each SCA tool's canonical report file by name (keeping its canonical name so the verifier's production {@code ReportParser} routes it). These slice the live
     * collect block out of the generated script and run it under a real POSIX {@code sh}.
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
            // No SCA find expression / no spotbugs name -> only JUnit reports are collected.
            assertThat(script).doesNotContain("spotbugsXml.xml").doesNotContain("collected_sca=$((collected_sca + 1))");
        }

        @Test
        void scaEnabled_collectsTheLanguageToolReports() {
            String script = factory().verifyScriptContent(exercise(ProgrammingLanguage.JAVA, true));
            // Java collects SpotBugs/Checkstyle/PMD/CPD report files by their canonical names, only the build-fresh ones.
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
            // The two SCA reports are collected under their canonical names, so the verifier's production ReportParser can route them.
            assertThat(collected.keySet()).anyMatch(n -> n.endsWith("spotbugsXml.xml")).anyMatch(n -> n.endsWith("checkstyle-result.xml"));
        }

        @EnabledOnOs({ LINUX, MAC })
        @Test
        void liveCollect_ignoresStaleScaReports(@TempDir Path tempDir) throws Exception {
            // A planted SpotBugs report older than the build-start marker must NOT be collected. The harness writes build files fresh, so we instead pre-plant + back-date by hand.
            Path buildDir = Files.createDirectories(tempDir.resolve("stale").resolve("build"));
            Path reportsDir = tempDir.resolve("stale").resolve("reports-root").resolve("solution");
            Path planted = buildDir.resolve("spotbugsXml.xml");
            VerifyScriptTestHarness.writeString(planted, "<BugCollection><BugInstance category=\"STYLE\"/></BugCollection>");
            Files.setLastModifiedTime(planted, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(7200)));
            Path marker = buildDir.resolve(".hyperion-build-start");
            VerifyScriptTestHarness.writeString(marker, "");
            Files.setLastModifiedTime(marker, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(3600)));

            String fullScript = factory().verifyScriptContent(exercise(ProgrammingLanguage.JAVA, true));
            String collectSnippet = VerifyScriptTestHarness.slice(fullScript, "rm -rf \"$REPORTS_DIR\"", "echo \"" + SandboxBuildCommandService.COLLECTED_MARKER);
            String script = "BUILD_DIR='" + buildDir + "'\nBUILD_START_MARKER='" + marker + "'\nREPORTS_DIR='" + reportsDir + "'\nrc=0\nseq=0\n" + collectSnippet + "\n";
            Path scriptFile = tempDir.resolve("stale-collect.sh");
            VerifyScriptTestHarness.writeString(scriptFile, script);
            VerifyScriptTestHarness.runSh(scriptFile);

            long collectedCount = Files.isDirectory(reportsDir) ? Files.list(reportsDir).count() : 0L;
            assertThat(collectedCount).as("a stale SCA report older than the build-start marker is not collected").isZero();
        }
    }
}
