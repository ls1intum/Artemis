package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;

/**
 * Deterministic unit test for the generated {@code verify.sh}: it must embed the exercise's real build phases with the CI directory placeholders substituted to the hermetic
 * layout, and aggregate test reports from both the phase's declared result paths and the common per-language locations — so non-Java languages (whose reports land in
 * {@code test-reports/}, not {@code surefire-reports/}) are detected. This guards the multi-language generalization without Docker.
 */
class SandboxBuildCommandServiceTest {

    private static SandboxBuildCommandService factoryWithPhases(List<BuildPhaseDTO> phases) {
        BuildPhasesTemplateService phasesService = mock(BuildPhasesTemplateService.class);
        when(phasesService.getDefaultBuildPlanPhasesFor(any())).thenReturn(phases);
        return new SandboxBuildCommandService(Optional.of(phasesService), Optional.of(new BuildScriptProviderService()));
    }

    /** Writes a UTF-8 text fixture via Apache {@link FileUtils} (the arch-mandated replacement for {@code Files.write*}), creating any missing parent directories. */
    private static void writeString(Path path, CharSequence content) throws IOException {
        FileUtils.writeStringToFile(path.toFile(), content.toString(), StandardCharsets.UTF_8);
    }

    /** Charset-explicit overload kept so the existing call sites that named the charset stay byte-identical. */
    private static void writeString(Path path, CharSequence content, Charset charset) throws IOException {
        FileUtils.writeStringToFile(path.toFile(), content.toString(), charset);
    }

    @Test
    void verifyScript_substitutesCiPlaceholders_andAggregatesPhaseResultPaths() {
        // A Python-like phase that cd's into the (placeholder) test working directory and writes its report under test-reports/.
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "cd ${testWorkingDirectory}\npytest --junitxml=test-reports/results.xml", null, false,
                List.of("test-reports/*results.xml"));
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(new ProgrammingExercise());

        // ${testWorkingDirectory} is substituted to the hermetic test root (".") in the build PHASE, not left as a literal that the shell would mis-expand. (The placeholder still
        // appears verbatim in the seeded-harness sed-substitution stanza, which is a separate, intentional mechanism — so we assert the PHASE form is gone, not the whole script.)
        assertThat(script).doesNotContain("cd ${testWorkingDirectory}").contains("cd .");
        // The phase's own report location and the common test-reports/ location are both searched, so pytest's JUnit XML is found.
        assertThat(script).contains("test-reports");
        // The result line now carries the per-run anti-forgery nonce suffix between the marker and the counters (HYPERION_RESULT$MARK_SUFFIX tests=).
        assertThat(script).contains("HYPERION_RESULT$MARK_SUFFIX tests=");
    }

    @Test
    void verifyScript_materializesTestsInSubdir_forLanguagesThatCheckOutTestsThere() {
        // Go/C/OCaml check the tests out into a "tests/" subdir, and their phases `cd ${testWorkingDirectory}`. The harness must place tests there and substitute the real path.
        ProgrammingExercise go = new ProgrammingExercise();
        go.setProgrammingLanguage(ProgrammingLanguage.GO);
        BuildPhaseDTO phase = new BuildPhaseDTO("test", "cd ${testWorkingDirectory}\ngo test ./...", null, false, List.of("${testWorkingDirectory}/test-results.xml"));
        String script = factoryWithPhases(List.of(phase)).verifyScriptContent(go);

        assertThat(script).contains("TEST_DEST=\"$BUILD_DIR/tests\"");
        // The build PHASE has its placeholder substituted to the real "tests/" checkout path; the verbatim placeholder still appears only in the seeded-harness sed stanza.
        assertThat(script).doesNotContain("cd ${testWorkingDirectory}").contains("cd tests");
    }

    @Test
    void verifyScript_fallsBackToConventionalBuild_whenNoPhases() {
        SandboxBuildCommandService factory = new SandboxBuildCommandService(Optional.empty(), Optional.empty());
        String script = factory.verifyScriptContent(new ProgrammingExercise());
        // Without phases it still produces a runnable recipe and the default report locations (surefire + test-reports) so a verdict can be read.
        assertThat(script).contains("mvn clean test").contains("surefire-reports").contains("test-reports");
    }

    @Test
    void buildCommands_targetTheVerifyScriptPerAssignment() {
        SandboxBuildCommandService factory = new SandboxBuildCommandService(Optional.empty(), Optional.empty());
        // The agent self-checks with its in-workspace copy.
        assertThat(factory.solutionBuildCommand()).isEqualTo("sh /workspace/verify.sh solution");
        assertThat(factory.templateBuildCommand()).isEqualTo("sh /workspace/verify.sh template");
        // The authoritative verifier runs the PRISTINE copy outside /workspace — the path the agent's tools (which only resolve under /workspace) could never have written — and
        // passes the per-run anti-forgery nonce as the second argument so the script stamps it onto every marker.
        assertThat(factory.pristineSolutionBuildCommand("HNabc123")).isEqualTo("sh /opt/hyperion/verify.sh solution 'HNabc123'");
        assertThat(factory.pristineTemplateBuildCommand("HNabc123")).isEqualTo("sh /opt/hyperion/verify.sh template 'HNabc123'");
    }

    @Test
    void verifyScript_substitutesCiPlaceholdersInsideTheCopiedTestHarness_soTheAgentNeverNeedsToEditIt() {
        // The Haskell-class fix: the script substitutes the CI directory placeholders inside the COPIED test files (e.g. test.cabal's hs-source-dirs), mapping both the student
        // parent and the solution working directory to "assignment" (the chosen assignment is always copied into assignment/), so a seeded harness with raw ${...} resolves against
        // the build tree without the agent having to touch it. Guards against a regression that drops the substitution and forces the agent to tamper with the immutable harness.
        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
        assertThat(script).contains("s#${studentParentWorkingDirectoryName}#assignment#g").contains("s#${solutionWorkingDirectory}#assignment#g")
                .contains("s#${studentWorkingDirectory}#/assignment/src#g").contains("s#${testWorkingDirectory}#.#g");
    }

    @Test
    void verifyScript_stampsTheAntiForgeryNonceOntoEveryMarker() {
        // Anti-forgery: the script reads the nonce from its second argument and stamps it onto every HYPERION_* marker (via $MARK_SUFFIX), so the verifier can reject a marker line
        // the agent's test code printed to stdout (which cannot carry the per-run nonce). Guards against a regression that drops the suffix and re-opens the stdout-marker forgery.
        String script = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
        assertThat(script).contains("NONCE=\"$2\"").contains("MARK_SUFFIX=\" $NONCE\"");
        // The emitter and the result line both carry the suffix.
        assertThat(script).contains("-v MARK=\"HYPERION_TESTNAME$MARK_SUFFIX\"").contains("-v FAILMARK=\"HYPERION_TESTFAIL$MARK_SUFFIX\"")
                .contains("echo \"HYPERION_RESULT$MARK_SUFFIX tests=");
    }

    /**
     * Drives the EXACT placeholder-substitution stanza from the live {@code verify.sh} against a fixture Haskell {@code test.cabal} under a real POSIX {@code sh}, so the shell
     * that
     * ships is the shell under test. Confirms the seeded raw {@code ${...}} placeholders resolve to the sandbox {@code assignment/} layout (and that a single-file Dart build never
     * has to edit the harness either): the produced cabal's hs-source-dirs point at {@code assignment/src} for BOTH the submission and the solution library, matching where the
     * verifier copies the chosen assignment.
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
            writeString(testDest.resolve("test.cabal"), seededCabal, StandardCharsets.UTF_8);

            String stanza = substitutionStanza();
            String script = "TEST_DEST='" + testDest + "'\n" + stanza + "\n";
            Path scriptFile = tempDir.resolve("subst.sh");
            writeString(scriptFile, script, StandardCharsets.UTF_8);
            Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("substitution stanza did not finish in time");
            }
            String produced = Files.readString(testDest.resolve("test.cabal"), StandardCharsets.UTF_8);
            assertThat(produced).doesNotContain("${").contains("hs-source-dirs: assignment/src");
            // Both libraries resolve to assignment/src — the verifier copies the chosen assignment into assignment/, so the solution library must NOT point at a missing solution/.
            assertThat(produced.split("hs-source-dirs: assignment/src", -1)).hasSizeGreaterThanOrEqualTo(3);
        }

        /** Slices the placeholder-substitution {@code find … | while … sed … done} stanza out of the live generated {@code verify.sh}. */
        private String substitutionStanza() {
            String fullScript = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
            int start = fullScript.indexOf("find \"$TEST_DEST\" -type f");
            int end = fullScript.indexOf("done", start) + "done".length();
            assertThat(start).isGreaterThanOrEqualTo(0);
            return fullScript.substring(start, end);
        }
    }

    /**
     * Runs the EXACT report-aggregation snippet from the generated {@code verify.sh} against fixture JUnit XML under a real POSIX {@code sh}, so the shell that ships is the shell
     * under test (the snippet is sliced out of the live script text, not re-implemented). This is the regression guard for the framework-semantics bug where Catch2's JUnit
     * reporter
     * sets {@code <testsuite tests="N">} to the assertion count, not the test-case count: the test count must be the number of {@code <testcase>} elements, which agrees with the
     * old
     * attribute sum for every other framework Artemis ships but is the only value that is the same between a passing solution and a failing (fewer-assertions) Catch2 template.
     */
    @Nested
    @EnabledOnOs({ LINUX, MAC })
    class ReportAggregation {

        /** A surefire/jest/nextest/pytest/…-style report: tests="N" attribute EQUALS the number of <testcase> elements (the invariant the change must preserve for all of them). */
        private static final String SUREFIRE_SOLUTION = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="StackTest" tests="3" failures="0" errors="0" skipped="0">
                  <testcase name="stack_initially_empty" classname="StackTest"/>
                  <testcase name="push_then_pop" classname="StackTest"/>
                  <testcase name="size_tracks_elements" classname="StackTest"/>
                </testsuite>
                """;

        private static final String SUREFIRE_TEMPLATE = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="StackTest" tests="3" failures="3" errors="0" skipped="0">
                  <testcase name="stack_initially_empty" classname="StackTest"><failure message="x"/></testcase>
                  <testcase name="push_then_pop" classname="StackTest"><failure message="x"/></testcase>
                  <testcase name="size_tracks_elements" classname="StackTest"><failure message="x"/></testcase>
                </testsuite>
                """;

        // Catch2 v3 JUnit reporter: tests="N" is the ASSERTION count. The solution passes all 14 assertions across 3 TEST_CASEs; the template fails them and — because REQUIRE is
        // fatal — aborts each TEST_CASE early, so only 7 assertions are evaluated. The <testcase> element count (3) is identical in both; the attribute disagrees (14 vs 7).
        private static final String CATCH2_SOLUTION = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites>
                  <testsuite name="<exe>" errors="0" failures="0" tests="14" hostname="tbd" time="0" timestamp="2026-06-03T00:00:00Z">
                    <testcase classname="global" name="stack_initially_empty" time="0"/>
                    <testcase classname="global" name="push_then_pop" time="0"/>
                    <testcase classname="global" name="size_tracks_elements" time="0"/>
                  </testsuite>
                </testsuites>
                """;

        private static final String CATCH2_TEMPLATE = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites>
                  <testsuite name="<exe>" errors="0" failures="3" tests="7" hostname="tbd" time="0" timestamp="2026-06-03T00:00:00Z">
                    <testcase classname="global" name="stack_initially_empty" time="0">
                      <failure message="empty() failed" type="REQUIRE">FAILED: REQUIRE( s.empty() )</failure>
                    </testcase>
                    <testcase classname="global" name="push_then_pop" time="0">
                      <failure message="pop() failed" type="REQUIRE">FAILED: REQUIRE( s.pop() == 1 )</failure>
                    </testcase>
                    <testcase classname="global" name="size_tracks_elements" time="0">
                      <failure message="size() failed" type="REQUIRE">FAILED: REQUIRE( s.size() == 2 )</failure>
                    </testcase>
                  </testsuite>
                </testsuites>
                """;

        @Test
        void catch2_testCount_isTestCaseCount_andEqualBetweenSolutionAndTemplate(@TempDir Path tempDir) throws Exception {
            Aggregate solution = aggregate(tempDir, "sol", CATCH2_SOLUTION);
            Aggregate template = aggregate(tempDir, "tpl", CATCH2_TEMPLATE);

            // The bug: under the old attribute-sum the solution reported tests=14 and the template tests=7, tripping the "different number of tests" gate. Counting <testcase>
            // elements yields 3 for BOTH, so the differential oracle sees the same suite and the template is no longer falsely rejected.
            assertThat(solution.tests()).isEqualTo(3);
            assertThat(template.tests()).isEqualTo(3);
            assertThat(template.tests()).isEqualTo(solution.tests());
            // The solution passes all its tests; the template fails at least one — the conditions the verdict actually checks.
            assertThat(solution.failures()).isZero();
            assertThat(solution.errors()).isZero();
            assertThat(template.failures()).isGreaterThan(0);
        }

        @Test
        void surefireStyle_testCount_unchanged_attributeEqualsTestCaseElements(@TempDir Path tempDir) throws Exception {
            // For a conventional JUnit reporter (tests="N" == #<testcase>), counting elements gives the same value the attribute sum gave: the change is a no-op for every
            // framework except Catch2.
            Aggregate solution = aggregate(tempDir, "sol", SUREFIRE_SOLUTION);
            Aggregate template = aggregate(tempDir, "tpl", SUREFIRE_TEMPLATE);

            assertThat(solution.tests()).isEqualTo(3);
            assertThat(template.tests()).isEqualTo(3);
            assertThat(solution.failures()).isZero();
            assertThat(template.failures()).isEqualTo(3);
        }

        /** The four aggregated counters {@code verify.sh} computes from the JUnit XML. */
        private record Aggregate(int tests, int failures, int errors, int skipped) {
        }

        /**
         * Writes the fixture XML into a fresh report directory and runs the real aggregation snippet (sliced from the generated {@code verify.sh}) against it under {@code sh},
         * returning the four counters the script would print. Executing the live snippet (rather than a reimplementation) is what makes this a regression guard for the shipped
         * shell.
         */
        private Aggregate aggregate(Path tempDir, String name, String reportXml) throws Exception {
            Path buildDir = Files.createDirectories(tempDir.resolve(name));
            // The aggregation now filters on -newer "$BUILD_START_MARKER"; create the marker with an OLD mtime so the report written below is strictly newer and is counted (the
            // real script stamps the marker just before the build phases, so genuine reports are always newer than it).
            Path marker = buildDir.resolve(".hyperion-build-start");
            writeString(marker, "");
            Files.setLastModifiedTime(marker, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(3600)));
            Path reportDir = Files.createDirectories(buildDir.resolve("test-results"));
            writeString(reportDir.resolve("results.xml"), reportXml, StandardCharsets.UTF_8);

            String script = "BUILD_DIR='" + buildDir + "'\nBUILD_START_MARKER='" + marker + "'\n" + aggregationSnippet()
                    + "\necho \"tests=$tests failures=$failures errors=$errors skipped=$skipped\"\n";
            Path scriptFile = tempDir.resolve(name + "-aggregate.sh");
            writeString(scriptFile, script, StandardCharsets.UTF_8);

            Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("aggregation snippet did not finish in time");
            }
            return parse(output);
        }

        /**
         * Slices the report-aggregation block out of the live generated {@code verify.sh}: from the {@code xml=$(find …)} line through {@code errors=$(sum_attr errors)} (the last
         * counter assignment, after the skipped-aware {@code tests} computation). This is the exact shell that ships, so a change to the script that regresses the counters fails
         * this test.
         */
        private String aggregationSnippet() {
            String fullScript = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
            int start = fullScript.indexOf("xml=$(find");
            int end = fullScript.indexOf("errors=$(sum_attr errors)");
            assertThat(start).as("aggregation block start marker present in verify.sh").isNotNegative();
            assertThat(end).as("aggregation block end marker present in verify.sh").isGreaterThan(start);
            return fullScript.substring(start, end + "errors=$(sum_attr errors)".length());
        }

        private static Aggregate parse(String output) {
            int tests = 0;
            int failures = 0;
            int errors = 0;
            int skipped = 0;
            for (String token : output.trim().split("\\s+")) {
                String[] kv = token.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                switch (kv[0]) {
                    case "tests" -> tests = Integer.parseInt(kv[1]);
                    case "failures" -> failures = Integer.parseInt(kv[1]);
                    case "errors" -> errors = Integer.parseInt(kv[1]);
                    case "skipped" -> skipped = Integer.parseInt(kv[1]);
                    default -> {
                    }
                }
            }
            return new Aggregate(tests, failures, errors, skipped);
        }
    }

    /**
     * Drives the EXACT planted-report mitigation from the live generated {@code verify.sh} under a real POSIX {@code sh}: the pre-phase report DELETE (so a planted
     * {@code surefire-reports/*.xml} the agent put in the tests tree, copied in with its old mtime, is removed before the build) PLUS the {@code -newer "$BUILD_START_MARKER"}
     * filter
     * on the aggregation {@code find} (so only reports the build wrote THIS run are summed). The two shipped find-expressions and the marker line are sliced out of the live
     * script;
     * a no-op stands in for the build phases, and the test simulates "the build wrote a fresh report" by writing one AFTER the marker — exactly the real ordering.
     */
    @Nested
    @EnabledOnOs({ LINUX, MAC })
    class PlantedReportMitigation {

        private static final String PLANTED_PASSING_REPORT = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="Planted" tests="3" failures="0" errors="0" skipped="0">
                  <testcase name="planted_a" classname="Planted"/>
                  <testcase name="planted_b" classname="Planted"/>
                  <testcase name="planted_c" classname="Planted"/>
                </testsuite>
                """;

        private static final String REAL_FAILING_REPORT = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="Real" tests="2" failures="2" errors="0" skipped="0">
                  <testcase name="real_a" classname="Real"><failure message="x"/></testcase>
                  <testcase name="real_b" classname="Real"><failure message="x"/></testcase>
                </testsuite>
                """;

        @Test
        void plantedReportIsDeletedBeforePhases_andOnlyReportsWrittenDuringTheBuildAreCounted(@TempDir Path tempDir) throws Exception {
            Path buildDir = Files.createDirectories(tempDir.resolve("build"));
            Path reportDir = Files.createDirectories(buildDir.resolve("surefire-reports"));
            // Plant a PASSING report with an OLD mtime, as a `cp -a` from the agent's tests tree would (mtime preserved). If the mitigation failed, this would be summed as 3
            // passing.
            Path planted = reportDir.resolve("planted.xml");
            writeString(planted, PLANTED_PASSING_REPORT, StandardCharsets.UTF_8);
            Files.setLastModifiedTime(planted, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(3600)));

            // The shipped delete + marker, then a stand-in "phase" that writes the REAL report (after the marker, like a real build), then the shipped aggregation.
            String script = "BUILD_DIR='" + buildDir + "'\n" + deleteAndMarkerSnippet() + "\n"
            // The build phase writes its own fresh report into a NEW report dir; sleep so its mtime is strictly newer than the marker on coarse-granularity filesystems.
                    + "sleep 1\nmkdir -p \"$BUILD_DIR/test-results\"\ncat > \"$BUILD_DIR/test-results/real.xml\" <<'XML'\n" + REAL_FAILING_REPORT + "XML\n" + aggregationSnippet()
                    + "\necho \"tests=$tests failures=$failures errors=$errors skipped=$skipped\"\n";
            Path scriptFile = tempDir.resolve("planted.sh");
            writeString(scriptFile, script, StandardCharsets.UTF_8);

            Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("planted-report snippet did not finish in time");
            }

            // The planted report must be gone, and only the real (failing) report counted: 2 tests, 2 failures — NOT 5 tests / 0 failures (which a summed planted report would
            // give).
            assertThat(planted).doesNotExist();
            assertThat(output).contains("tests=2").contains("failures=2");
        }

        /** Slices the shipped pre-phase report DELETE line through the {@code BUILD_START_MARKER} creation out of the live {@code verify.sh}. */
        private String deleteAndMarkerSnippet() {
            String fullScript = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
            int start = fullScript.indexOf("find \"$BUILD_DIR\" -type f");
            int end = fullScript.indexOf(": > \"$BUILD_START_MARKER\"");
            assertThat(start).as("pre-phase report delete present in verify.sh").isNotNegative();
            assertThat(end).as("build-start marker creation present in verify.sh").isGreaterThan(start);
            return fullScript.substring(start, end + ": > \"$BUILD_START_MARKER\"".length());
        }

        /** Slices the report-aggregation block (now carrying the {@code -newer "$BUILD_START_MARKER"} filter) out of the live {@code verify.sh}. */
        private String aggregationSnippet() {
            String fullScript = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
            int start = fullScript.indexOf("xml=$(find");
            int end = fullScript.indexOf("errors=$(sum_attr errors)");
            assertThat(start).isNotNegative();
            assertThat(end).isGreaterThan(start);
            assertThat(fullScript.substring(start, end)).as("aggregation find must filter on the build-start marker").contains("-newer \"$BUILD_START_MARKER\"");
            return fullScript.substring(start, end + "errors=$(sum_attr errors)".length());
        }
    }

    /**
     * Runs the EXACT {@code emit_test_lines} block from the generated {@code verify.sh} (the awk that composes each test's recorded name and flags failing testcases) against
     * fixture
     * JUnit XML under a real POSIX {@code sh}. The block is sliced out of the live script text, so the shell that ships is the shell under test. This is the regression guard for
     * the
     * oracle-vs-production name-parity fix: the {@code HYPERION_TESTNAME}/{@code HYPERION_TESTFAIL} lines must be byte-identical to what
     * {@code de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser} records in production — including the singular-top-level-suite exception (no prefix).
     */
    @Nested
    @EnabledOnOs({ LINUX, MAC })
    class TestNameComposition {

        /**
         * Dart/tojunit shape: the file-suite {@code test.palindrome} is NESTED (not the singular top-level), so production dot-prefixes it onto the package:test group+test name.
         */
        private static final String DART = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites>
                  <testsuite name="All Tests">
                    <testsuite name="test.palindrome">
                      <testcase name="reverseString reverse_non_empty"/>
                      <testcase name="isPalindrome palindrome_true"><failure message="boom"/></testcase>
                    </testsuite>
                  </testsuite>
                </testsuites>
                """;

        /** Rust/nextest shape: a SINGULAR top-level {@code <testsuite name="crate::behavior">} contributes NO prefix, so the recorded name is the bare {@code test_x}. */
        private static final String RUST = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites>
                  <testsuite name="crate::behavior">
                    <testcase name="test_fibonacci_of_0"/>
                    <testcase name="test_fibonacci_of_10"><failure message="x"/></testcase>
                  </testsuite>
                </testsuites>
                """;

        /**
         * Surefire/Catch2 shape: a singular root {@code <testsuite>} (no {@code <testsuites>} wrapper) -> bare names; {@code classname=} must NOT be mistaken for {@code name=}.
         */
        private static final String SUREFIRE = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="StackTest" tests="2">
                  <testcase classname="StackTest" name="stack_initially_empty"/>
                  <testcase classname="StackTest" name="push_then_pop"><failure message="x"/></testcase>
                </testsuite>
                """;

        /** Multiple top-level suites under {@code <testsuites>}: each top-level suite name IS included (production's plural-top-level rule). */
        private static final String MULTI = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites>
                  <testsuite name="SuiteA"><testcase name="t1"/></testsuite>
                  <testsuite name="SuiteB"><testcase name="t2"><error message="x"/></testcase></testsuite>
                </testsuites>
                """;

        @Test
        void dart_nestedFileSuite_isDotPrefixedOntoTheGroupAndTestName(@TempDir Path tempDir) throws Exception {
            Emitted e = emit(tempDir, "dart", DART);
            assertThat(e.names()).containsExactly("test.palindrome.reverseString reverse_non_empty", "test.palindrome.isPalindrome palindrome_true");
            assertThat(e.failed()).containsExactly("test.palindrome.isPalindrome palindrome_true");
        }

        @Test
        void rust_singularTopLevelSuite_yieldsBareNames(@TempDir Path tempDir) throws Exception {
            Emitted e = emit(tempDir, "rust", RUST);
            assertThat(e.names()).containsExactly("test_fibonacci_of_0", "test_fibonacci_of_10");
            assertThat(e.failed()).containsExactly("test_fibonacci_of_10");
        }

        @Test
        void surefire_singularRootSuite_yieldsBareNames_andIgnoresClassname(@TempDir Path tempDir) throws Exception {
            Emitted e = emit(tempDir, "sure", SUREFIRE);
            assertThat(e.names()).containsExactly("stack_initially_empty", "push_then_pop");
            assertThat(e.failed()).containsExactly("push_then_pop");
        }

        @Test
        void multipleTopLevelSuites_includeEachSuiteName(@TempDir Path tempDir) throws Exception {
            Emitted e = emit(tempDir, "multi", MULTI);
            assertThat(e.names()).containsExactly("SuiteA.t1", "SuiteB.t2");
            assertThat(e.failed()).containsExactly("SuiteB.t2");
        }

        /** The {@code HYPERION_TESTNAME} and {@code HYPERION_TESTFAIL} names the emit block printed for a fixture. */
        @Test
        void emit_stampsTheNonceOntoEveryLine_whenMarkSuffixIsSet(@TempDir Path tempDir) throws Exception {
            // The live emit awk uses MARK="HYPERION_TESTNAME$MARK_SUFFIX"; when the verifier passes a nonce (MARK_SUFFIX=" <nonce>"), every emitted name/fail line carries it, so
            // the
            // nonce-anchored parser honors them — and a same-named line WITHOUT the nonce (an agent test's stdout forgery) is distinguishable.
            Emitted e = emit(tempDir, "nonce", RUST, " HN-the-nonce");
            assertThat(e.names()).containsExactly("HN-the-nonce test_fibonacci_of_0", "HN-the-nonce test_fibonacci_of_10");
            assertThat(e.failed()).containsExactly("HN-the-nonce test_fibonacci_of_10");
        }

        private record Emitted(List<String> names, List<String> failed) {
        }

        private Emitted emit(Path tempDir, String name, String reportXml) throws Exception {
            return emit(tempDir, name, reportXml, "");
        }

        private Emitted emit(Path tempDir, String name, String reportXml, String markSuffix) throws Exception {
            Path reportDir = Files.createDirectories(tempDir.resolve(name).resolve("test-results"));
            Path report = reportDir.resolve("results.xml");
            writeString(report, reportXml, StandardCharsets.UTF_8);

            // Drive the live emit block with xml set to the fixture report, exactly as verify.sh sets it from `find`; MARK_SUFFIX models the per-run nonce the verifier passes.
            String script = "MARK_SUFFIX='" + markSuffix + "'\nxml='" + report + "'\n" + emitSnippet() + "\n";
            Path scriptFile = tempDir.resolve(name + "-emit.sh");
            writeString(scriptFile, script, StandardCharsets.UTF_8);

            Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("emit snippet did not finish in time");
            }
            List<String> names = new java.util.ArrayList<>();
            List<String> failed = new java.util.ArrayList<>();
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

        /**
         * Slices the {@code emit_test_lines()} function definition AND its invocation out of the live generated {@code verify.sh}: from {@code emit_test_lines() {} through the
         * standalone {@code emit_test_lines} call line. This is the exact shell that ships, so a change to the awk that regresses the recorded names fails this test.
         */
        private String emitSnippet() {
            String fullScript = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
            int start = fullScript.indexOf("emit_test_lines() {");
            assertThat(start).as("emit_test_lines definition present in verify.sh").isNotNegative();
            int call = fullScript.indexOf("\nemit_test_lines\n", start);
            assertThat(call).as("emit_test_lines invocation present in verify.sh").isGreaterThan(start);
            return fullScript.substring(start, call + "\nemit_test_lines\n".length());
        }
    }

    /**
     * Drives the WHOLE shipped aggregation+emit pipeline (the report scrub, {@code count_testcases}, {@code sum_attr}, {@code emit_test_lines}, and the {@code HYPERION_RESULT}
     * line) from the live generated {@code verify.sh} under a real POSIX {@code sh} against an agent-controlled report. The block is sliced from {@code xml=$(find …)} through the
     * {@code echo "HYPERION_RESULT …"} line, so the scrub the verifier runs is the scrub under test. This is the regression guard for the report-text forgery hole: the
     * name/count/failure extraction uses grep/awk, NOT a real XML parser, so a graded test that genuinely PASSES could otherwise forge a FAILED-looking verdict by printing
     * markup-looking text (a phantom {@code <testcase>}, a forged {@code failures="N"}, a stray {@code HYPERION_TESTFAIL}) into its own captured
     * {@code <system-out>}/{@code <failure>}/CDATA/comment — none of which production's {@code TestResultXmlParser} (Jackson {@code XmlMapper}) treats as structure. The scrub must
     * delete that character data before the grep/awk, so the oracle's counts and names match production on the well-formed XML.
     */
    @Nested
    @EnabledOnOs({ LINUX, MAC })
    class ReportTextForgery {

        /**
         * A single test that genuinely PASSES (no {@code <failure>}/{@code <error>} child) but stuffs its captured {@code <system-out>} CDATA with a phantom {@code <testcase>}, a
         * forged {@code failures="50"}, and a stray {@code HYPERION_TESTFAIL} for itself. Production sees ONE passing testcase, zero failures; the unscrubbed grep/awk saw two
         * testcases, fifty failures, and a fail line — the exact forgery this fix closes.
         */
        private static final String SYSTEM_OUT_CDATA_FORGERY = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="StackTest" tests="1" failures="0" errors="0" skipped="0">
                  <testcase name="peek_returns_top" classname="StackTest">
                    <system-out><![CDATA[ <testcase name="fake"/> failures="50" HYPERION_TESTFAIL peek_returns_top ]]></system-out>
                  </testcase>
                </testsuite>
                """;

        /** Same forgery, but planted in an XML COMMENT and a plain (non-CDATA) {@code <system-out>} body rather than a CDATA section. */
        private static final String COMMENT_AND_SYSTEM_OUT_FORGERY = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="StackTest" tests="1" failures="0" errors="0" skipped="0">
                  <!-- <testcase name="ghost"/> failures="99" HYPERION_TESTFAIL peek_returns_top -->
                  <testcase name="peek_returns_top" classname="StackTest">
                    <system-out>log line: testcase name="fake2" failures="7" HYPERION_TESTFAIL peek_returns_top</system-out>
                  </testcase>
                </testsuite>
                """;

        /**
         * A test that genuinely fails ONCE: a real {@code <failure>} whose detailed-message body (which production records as text, never as structure) embeds a phantom
         * {@code <testcase>}, a forged {@code failures="40"}, AND a literal {@code </failure>} hidden inside CDATA that must NOT prematurely end the suppression. Production
         * records
         * one failing testcase; the unscrubbed grep/awk inflated the count and the failure attribute.
         */
        private static final String FAILURE_BODY_FORGERY = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="StackTest" tests="1" failures="1" errors="0" skipped="0">
                  <testcase name="really_fails" classname="StackTest">
                    <failure message="boom"><![CDATA[ </failure> <testcase name="fake"/> failures="40" ]]> trailing <testcase name="fake2"/></failure>
                  </testcase>
                </testsuite>
                """;

        @Test
        void systemOutCdataForgery_isNeutralized_passingTestStaysPassing(@TempDir Path tempDir) throws Exception {
            Result r = run(tempDir, "cdata", SYSTEM_OUT_CDATA_FORGERY);
            // Production parses ONE passing testcase, zero failures. The forged second <testcase>, failures="50", and HYPERION_TESTFAIL must all be scrubbed away.
            assertThat(r.tests()).isEqualTo(1);
            assertThat(r.failures()).isZero();
            assertThat(r.errors()).isZero();
            assertThat(r.testnames()).containsExactly("peek_returns_top");
            assertThat(r.testfails()).isEmpty();
        }

        @Test
        void commentAndSystemOutForgery_isNeutralized(@TempDir Path tempDir) throws Exception {
            Result r = run(tempDir, "comment", COMMENT_AND_SYSTEM_OUT_FORGERY);
            assertThat(r.tests()).isEqualTo(1);
            assertThat(r.failures()).isZero();
            assertThat(r.testnames()).containsExactly("peek_returns_top");
            assertThat(r.testfails()).isEmpty();
        }

        @Test
        void failureBodyForgery_isNeutralized_oneRealFailureNoPhantoms(@TempDir Path tempDir) throws Exception {
            Result r = run(tempDir, "failbody", FAILURE_BODY_FORGERY);
            // The real <failure> element survives (its start tag is kept), so the testcase is counted once and flagged failed; the body's phantom <testcase>s and forged
            // failures="40" are gone, and the CDATA's literal </failure> did not prematurely end the suppression (which would have leaked the trailing phantom <testcase>).
            assertThat(r.tests()).isEqualTo(1);
            assertThat(r.failures()).isEqualTo(1);
            assertThat(r.testnames()).containsExactly("really_fails");
            assertThat(r.testfails()).containsExactly("really_fails");
        }

        @Test
        void validReportWithNoForgery_parsesUnchanged(@TempDir Path tempDir) throws Exception {
            // Parity guard: a clean report with a real failure and a real <system-out> log (no markup) must still count and name correctly after scrubbing — the scrub must not
            // over-reject valid reports.
            String valid = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <testsuite name="StackTest" tests="2" failures="1" errors="0" skipped="0">
                      <testcase name="pushes_value" classname="StackTest">
                        <system-out>pushed 1, size now 1</system-out>
                      </testcase>
                      <testcase name="pops_value" classname="StackTest">
                        <failure message="expected 1 but was 0">stack underflow at line 7</failure>
                      </testcase>
                    </testsuite>
                    """;
            Result r = run(tempDir, "valid", valid);
            assertThat(r.tests()).isEqualTo(2);
            assertThat(r.failures()).isEqualTo(1);
            assertThat(r.testnames()).containsExactly("pushes_value", "pops_value");
            assertThat(r.testfails()).containsExactly("pops_value");
        }

        /** The counters and the emitted test-name / failing-test-name lines the shipped pipeline produced for one report. */
        private record Result(int tests, int failures, int errors, int skipped, List<String> testnames, List<String> testfails) {
        }

        /**
         * Writes the fixture report into a fresh build tree (with an OLD build-start marker so the report is counted), then runs the live aggregation+emit+result block (sliced
         * from {@code xml=$(find …)} through the {@code echo "HYPERION_RESULT …"} line) under {@code sh}, returning the counters and the emitted name/fail lines. Running the
         * shipped block (scrub included) is what makes this a regression guard for the report-text forgery.
         */
        private Result run(Path tempDir, String name, String reportXml) throws Exception {
            Path buildDir = Files.createDirectories(tempDir.resolve(name));
            Path marker = buildDir.resolve(".hyperion-build-start");
            writeString(marker, "");
            Files.setLastModifiedTime(marker, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(3600)));
            Path reportDir = Files.createDirectories(buildDir.resolve("test-results"));
            writeString(reportDir.resolve("results.xml"), reportXml, StandardCharsets.UTF_8);

            String script = "BUILD_DIR='" + buildDir + "'\nBUILD_START_MARKER='" + marker + "'\nMARK_SUFFIX=''\nrc=0\n" + pipelineSnippet() + "\n";
            Path scriptFile = tempDir.resolve(name + "-pipeline.sh");
            writeString(scriptFile, script, StandardCharsets.UTF_8);

            Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("forgery pipeline did not finish in time");
            }
            int tests = 0;
            int failures = 0;
            int errors = 0;
            int skipped = 0;
            List<String> testnames = new java.util.ArrayList<>();
            List<String> testfails = new java.util.ArrayList<>();
            for (String line : output.split("\n")) {
                if (line.startsWith("HYPERION_TESTNAME ")) {
                    testnames.add(line.substring("HYPERION_TESTNAME ".length()).trim());
                }
                else if (line.startsWith("HYPERION_TESTFAIL ")) {
                    testfails.add(line.substring("HYPERION_TESTFAIL ".length()).trim());
                }
                else if (line.startsWith("HYPERION_RESULT")) {
                    for (String token : line.split("\\s+")) {
                        String[] kv = token.split("=", 2);
                        if (kv.length != 2) {
                            continue;
                        }
                        switch (kv[0]) {
                            case "tests" -> tests = Integer.parseInt(kv[1]);
                            case "failures" -> failures = Integer.parseInt(kv[1]);
                            case "errors" -> errors = Integer.parseInt(kv[1]);
                            case "skipped" -> skipped = Integer.parseInt(kv[1]);
                            default -> {
                            }
                        }
                    }
                }
            }
            return new Result(tests, failures, errors, skipped, testnames, testfails);
        }

        /**
         * Slices the whole aggregation+emit+result region out of the live generated {@code verify.sh}: from {@code xml=$(find …)} through the {@code echo "HYPERION_RESULT …"}
         * line. This includes the report scrub the fix adds, so the snippet under test is the exact shell that ships.
         */
        private String pipelineSnippet() {
            String fullScript = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
            int start = fullScript.indexOf("xml=$(find");
            assertThat(start).as("aggregation block start present in verify.sh").isNotNegative();
            int resultLine = fullScript.indexOf("echo \"HYPERION_RESULT", start);
            assertThat(resultLine).as("HYPERION_RESULT echo present in verify.sh").isGreaterThan(start);
            int end = fullScript.indexOf('\n', resultLine);
            return fullScript.substring(start, end);
        }
    }

    /**
     * The strongest soundness guard: it runs the SAME report through (a) production's real XML parser
     * {@link de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser} (Jackson {@code XmlMapper}) and (b) the live scrubbed {@code verify.sh} pipeline under a real
     * {@code sh}, and asserts the recorded test NAMES, the passing/failing partition, and the testcase count are byte-identical — for the forgery shapes AND the tricky valid
     * shapes
     * (Dart nested suite, Rust singular top-level suite, multiple top-level suites, surefire with {@code classname=}, {@code <failure>}/{@code <error>} with embedded
     * markup-looking
     * bodies). If the scrubber ever diverged from what production records, this test fails. This is the actual answer to "is the oracle's name/count extraction sound against
     * agent-controlled report text" — it is sound exactly insofar as it tracks the real parser, which this cross-check pins.
     */
    @Nested
    @EnabledOnOs({ LINUX, MAC })
    class ProductionParityCrossCheck {

        @Test
        void cdataForgery_matchesProduction(@TempDir Path tempDir) throws Exception {
            assertParity(tempDir, "cdata", ReportTextForgery.SYSTEM_OUT_CDATA_FORGERY);
        }

        @Test
        void commentForgery_matchesProduction(@TempDir Path tempDir) throws Exception {
            assertParity(tempDir, "comment", ReportTextForgery.COMMENT_AND_SYSTEM_OUT_FORGERY);
        }

        @Test
        void failureBodyForgery_matchesProduction(@TempDir Path tempDir) throws Exception {
            assertParity(tempDir, "failbody", ReportTextForgery.FAILURE_BODY_FORGERY);
        }

        @Test
        void dartNestedSuite_matchesProduction(@TempDir Path tempDir) throws Exception {
            assertParity(tempDir, "dart", TestNameComposition.DART);
        }

        @Test
        void rustSingularSuite_matchesProduction(@TempDir Path tempDir) throws Exception {
            assertParity(tempDir, "rust", TestNameComposition.RUST);
        }

        @Test
        void surefireSingularRoot_matchesProduction(@TempDir Path tempDir) throws Exception {
            assertParity(tempDir, "surefire", TestNameComposition.SUREFIRE);
        }

        @Test
        void multipleTopLevelSuites_matchProduction(@TempDir Path tempDir) throws Exception {
            assertParity(tempDir, "multi", TestNameComposition.MULTI);
        }

        @Test
        void validReportWithLogs_matchesProduction(@TempDir Path tempDir) throws Exception {
            String valid = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <testsuite name="StackTest" tests="2" failures="1" errors="0" skipped="0">
                      <testcase name="pushes_value" classname="StackTest"><system-out>pushed 1, size now 1</system-out></testcase>
                      <testcase name="pops_value" classname="StackTest"><failure message="exp 1 got 0">underflow at line 7</failure></testcase>
                    </testsuite>
                    """;
            assertParity(tempDir, "validlogs", valid);
        }

        /**
         * Asserts the live scrubbed pipeline's emitted names/partition/count equal what {@link TestResultXmlParser} records for the same report. Production composes a testcase's
         * recorded name as the dot-joined enclosing testsuite names prepended to the testcase name, dropping a SINGULAR top-level suite — the exact rule the emit awk reproduces —
         * so the two name sets must be equal sets (order is irrelevant to the [task]-binding check the oracle does against them).
         */
        private void assertParity(Path tempDir, String name, String reportXml) throws Exception {
            // Production view.
            List<LocalCITestJobDTO> failed = new ArrayList<>();
            List<LocalCITestJobDTO> ok = new ArrayList<>();
            TestResultXmlParser.processTestResultFile(reportXml, failed, ok);
            List<String> prodPass = ok.stream().map(LocalCITestJobDTO::name).sorted().toList();
            List<String> prodFail = failed.stream().map(LocalCITestJobDTO::name).sorted().toList();
            int prodTotal = ok.size() + failed.size();

            // Oracle view: the live scrubbed pipeline.
            PipelineRun run = runPipeline(tempDir, name, reportXml);
            List<String> oraclePass = run.names().stream().filter(n -> !run.failed().contains(n)).sorted().toList();
            List<String> oracleFail = run.failed().stream().sorted().toList();

            assertThat(run.tests()).as("[%s] testcase count parity", name).isEqualTo(prodTotal);
            assertThat(oracleFail).as("[%s] failing-test NAME parity with TestResultXmlParser", name).isEqualTo(prodFail);
            assertThat(oraclePass).as("[%s] passing-test NAME parity with TestResultXmlParser", name).isEqualTo(prodPass);
            // The reported failures counter must not exceed the real failing-testcase count (a forged failures="N" would blow this).
            assertThat(run.failures()).as("[%s] failures counter not inflated above real failing testcases", name).isLessThanOrEqualTo(prodFail.size());
        }

        private record PipelineRun(int tests, int failures, List<String> names, List<String> failed) {
        }

        private PipelineRun runPipeline(Path tempDir, String name, String reportXml) throws Exception {
            Path buildDir = Files.createDirectories(tempDir.resolve(name));
            Path marker = buildDir.resolve(".hyperion-build-start");
            writeString(marker, "");
            Files.setLastModifiedTime(marker, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(3600)));
            Path reportDir = Files.createDirectories(buildDir.resolve("test-results"));
            writeString(reportDir.resolve("results.xml"), reportXml, StandardCharsets.UTF_8);

            String fullScript = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
            int start = fullScript.indexOf("xml=$(find");
            int resultLine = fullScript.indexOf("echo \"HYPERION_RESULT", start);
            int end = fullScript.indexOf('\n', resultLine);
            String snippet = fullScript.substring(start, end);

            String script = "BUILD_DIR='" + buildDir + "'\nBUILD_START_MARKER='" + marker + "'\nMARK_SUFFIX=''\nrc=0\n" + snippet + "\n";
            Path scriptFile = tempDir.resolve(name + "-parity.sh");
            writeString(scriptFile, script, StandardCharsets.UTF_8);

            Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("parity pipeline did not finish in time");
            }
            int tests = 0;
            int failures = 0;
            List<String> names = new ArrayList<>();
            List<String> failedNames = new ArrayList<>();
            for (String line : output.split("\n")) {
                if (line.startsWith("HYPERION_TESTNAME ")) {
                    names.add(line.substring("HYPERION_TESTNAME ".length()).trim());
                }
                else if (line.startsWith("HYPERION_TESTFAIL ")) {
                    failedNames.add(line.substring("HYPERION_TESTFAIL ".length()).trim());
                }
                else if (line.startsWith("HYPERION_RESULT")) {
                    for (String token : line.split("\\s+")) {
                        String[] kv = token.split("=", 2);
                        if (kv.length != 2) {
                            continue;
                        }
                        if (kv[0].equals("tests")) {
                            tests = Integer.parseInt(kv[1]);
                        }
                        else if (kv[0].equals("failures")) {
                            failures = Integer.parseInt(kv[1]);
                        }
                    }
                }
            }
            return new PipelineRun(tests, failures, names, failedNames);
        }
    }

    /**
     * The static-code-analysis emission (Defect D2). When SCA is disabled the generated script must contain NO SCA section (so a non-SCA exercise's verdict is byte-for-byte
     * unchanged); when enabled it must emit one {@code HYPERION_SCA <TOOL>|<rawCategory>} line per finding, extracting the SAME raw category production's parsers do. These slice
     * the
     * live SCA block out of the generated script and run it under a real POSIX {@code sh} against real-shaped SpotBugs/Checkstyle/ruff reports.
     */
    @Nested
    class StaticCodeAnalysisEmission {

        private SandboxBuildCommandService factory() {
            BuildPhasesTemplateService phasesService = mock(BuildPhasesTemplateService.class);
            // The SCA section is keyed off the exercise language + scaEnabled, not the phases; give a single trivial phase so a recipe resolves.
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
        void scaDisabled_emitsNoScaSection_soNonScaScriptIsUnchanged() {
            String script = factory().verifyScriptContent(exercise(ProgrammingLanguage.JAVA, false));
            assertThat(script).doesNotContain("HYPERION_SCA").doesNotContain("emit_sca_for");
        }

        @Test
        void scaEnabled_emitsScaSectionScanningTheLanguageToolReports() {
            String script = factory().verifyScriptContent(exercise(ProgrammingLanguage.JAVA, true));
            // Java scans SpotBugs/Checkstyle/PMD/CPD report files by their canonical names.
            assertThat(script).contains("HYPERION_SCA").contains("emit_sca_for 'spotbugsXml.xml'").contains("emit_sca_for 'checkstyle-result.xml'")
                    .contains("emit_sca_for 'pmd.xml'").contains("emit_sca_for 'cpd.xml'");
            // Anti-forgery: only reports written this run (newer than the build-start marker) are scanned.
            assertThat(script).contains("-newer \"$BUILD_START_MARKER\"");
        }

        @Test
        void scaEnabled_python_scansRuffSarif() {
            String script = factory().verifyScriptContent(exercise(ProgrammingLanguage.PYTHON, true));
            assertThat(script).contains("emit_sca_for 'ruff.sarif'").contains("RUFF|*");
        }

        /**
         * The generated SCA section must be valid POSIX {@code sh} for EVERY SCA-capable language (the per-tool awk programs are embedded in single-quoted sh strings, so a stray
         * quote or unbalanced construct would only surface at run time). {@code sh -n} parses without executing, catching any syntax break across all languages at once.
         */
        @EnabledOnOs({ LINUX, MAC })
        @Test
        void scaSectionIsValidPosixShell_forEveryScaCapableLanguage(@TempDir Path tempDir) throws Exception {
            for (ProgrammingLanguage language : List.of(ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.DART, ProgrammingLanguage.JAVA,
                    ProgrammingLanguage.JAVASCRIPT, ProgrammingLanguage.PYTHON, ProgrammingLanguage.R, ProgrammingLanguage.RUBY, ProgrammingLanguage.RUST,
                    ProgrammingLanguage.TYPESCRIPT)) {
                String fullScript = factory().verifyScriptContent(exercise(language, true));
                assertThat(fullScript).as("%s has an SCA section when SCA is enabled", language).contains("emit_sca_for() {");
                Path scriptFile = tempDir.resolve("full-" + language.name().toLowerCase() + ".sh");
                writeString(scriptFile, fullScript, StandardCharsets.UTF_8);
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
        void liveScaSection_extractsSpotbugsAndCheckstyleCategories_matchingProduction(@TempDir Path tempDir) throws Exception {
            Path buildDir = Files.createDirectories(tempDir.resolve("build"));
            Path marker = buildDir.resolve(".hyperion-build-start");
            writeString(marker, "");
            Files.setLastModifiedTime(marker, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(3600)));
            writeString(buildDir.resolve("spotbugsXml.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <BugCollection version="4.7.3">
                      <BugInstance type="DM_DEFAULT_ENCODING" priority="2" category="STYLE"><SourceLine start="12" end="12"/></BugInstance>
                      <BugInstance type="EI_EXPOSE_REP" priority="1" category="MALICIOUS_CODE"><SourceLine start="3" end="3"/></BugInstance>
                    </BugCollection>
                    """, StandardCharsets.UTF_8);
            writeString(buildDir.resolve("checkstyle-result.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <checkstyle version="10.3">
                      <file name="Stack.java">
                        <error line="3" severity="warning" message="x" source="com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocTypeCheck"/>
                        <error line="9" severity="error" message="y" source="com.puppycrawl.tools.checkstyle.checks.design.DesignForExtensionCheck"/>
                        <error line="1" severity="error" message="z" source="com.puppycrawl.tools.checkstyle.checks.MiscCheck"/>
                      </file>
                    </checkstyle>
                    """, StandardCharsets.UTF_8);

            List<String> findings = runScaSection(tempDir, "java", ProgrammingLanguage.JAVA, buildDir, marker);

            // SpotBugs: the category attribute verbatim (SpotbugsParser). Checkstyle: the second-to-last source segment, with the directly-under-checks rule => miscellaneous
            // (CheckstyleParser.extractCategory).
            assertThat(findings).contains("SPOTBUGS|STYLE", "SPOTBUGS|MALICIOUS_CODE", "CHECKSTYLE|javadoc", "CHECKSTYLE|design", "CHECKSTYLE|miscellaneous");
        }

        @EnabledOnOs({ LINUX, MAC })
        @Test
        void liveScaSection_ignoresStaleReports_writtenBeforeTheBuildStartMarker(@TempDir Path tempDir) throws Exception {
            Path buildDir = Files.createDirectories(tempDir.resolve("build"));
            // A planted SpotBugs report whose mtime predates the build-start marker must NOT be scanned (anti-forgery, same as the JUnit aggregation).
            Path planted = buildDir.resolve("spotbugsXml.xml");
            writeString(planted, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <BugCollection><BugInstance type="X" category="STYLE"><SourceLine start="1" end="1"/></BugInstance></BugCollection>
                    """, StandardCharsets.UTF_8);
            Files.setLastModifiedTime(planted, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(7200)));
            Path marker = buildDir.resolve(".hyperion-build-start");
            writeString(marker, "");
            Files.setLastModifiedTime(marker, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(3600)));

            List<String> findings = runScaSection(tempDir, "stale", ProgrammingLanguage.JAVA, buildDir, marker);
            assertThat(findings).as("a report older than the build-start marker is not scanned").isEmpty();
        }

        /** Slices the SCA block (the {@code emit_sca_for} function plus its per-tool calls) out of the live generated script and runs it under sh with BUILD_DIR/marker bound. */
        private List<String> runScaSection(Path tempDir, String name, ProgrammingLanguage language, Path buildDir, Path marker) throws Exception {
            String fullScript = factory().verifyScriptContent(exercise(language, true));
            int start = fullScript.indexOf("emit_sca_for() {");
            assertThat(start).as("the SCA section must be present when SCA is enabled").isGreaterThanOrEqualTo(0);
            int end = fullScript.indexOf("echo \"" + "HYPERION_RESULT", start);
            assertThat(end).isGreaterThan(start);
            String scaSection = fullScript.substring(start, end);

            String script = "BUILD_DIR='" + buildDir + "'\nBUILD_START_MARKER='" + marker + "'\nMARK_SUFFIX=''\n" + scaSection + "\n";
            Path scriptFile = tempDir.resolve(name + "-sca.sh");
            writeString(scriptFile, script, StandardCharsets.UTF_8);
            Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("sca section did not finish in time");
            }
            List<String> findings = new ArrayList<>();
            for (String line : output.split("\n")) {
                if (line.startsWith("HYPERION_SCA ")) {
                    findings.add(line.substring("HYPERION_SCA ".length()).trim());
                }
            }
            return findings;
        }
    }
}
