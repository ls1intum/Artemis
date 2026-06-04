package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Adversarial parity probe: does the sandbox {@code verify.sh} oracle ACCEPT imply the persisted exercise grades correctly in PRODUCTION?
 * <p>
 * This test does NOT spin up Docker or the GPU. It drives the EXACT shell snippets the shipped {@code verify.sh} contains (sliced out of the live script text, run under a real
 * POSIX
 * {@code sh}) and compares their verdict to the PRODUCTION grading semantics ({@link TestResultXmlParser} — the parser the real LocalCI pipeline uses) on byte-identical JUnit XML.
 * Where the two disagree on the SAME report, the sandbox oracle can accept an exercise that the real pipeline grades wrong. Each divergence is reproduced, not asserted from
 * theory.
 * <p>
 * The single high-value divergence reproduced here is SKIPPED tests: production drops a {@code <testcase><skipped/></testcase>} entirely (it is neither successful nor failed, so
 * the
 * synced test case is graded as "not executed" = 0 credit), while {@code verify.sh} counts the skipped {@code <testcase>} element toward the solution test count and emits a name
 * for
 * it but no failure — so the differential oracle treats the solution as a full pass.
 */
class SandboxProductionParityDivergenceTest {

    // A solution-build report where ONE test is reported <skipped/> (e.g. JUnit @Disabled / an assumption that did not hold, pytest skip, Go t.Skip via go-junit-report). The other
    // two pass. Production: the skipped case is dropped (neither successful nor failed); verify.sh: it is counted as a testcase and its name emitted, with no failure.
    private static final String SOLUTION_WITH_SKIP = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="StackTest" tests="3" failures="0" errors="0" skipped="1">
              <testcase name="push_then_pop" classname="StackTest"/>
              <testcase name="size_tracks_elements" classname="StackTest"/>
              <testcase name="peek_does_not_remove" classname="StackTest"><skipped/></testcase>
            </testsuite>
            """;

    // The SAME suite on the template: the two real tests fail, and the same test is still skipped. The template "fails the tests"; the skipped one is again dropped by production
    // and
    // emitted-without-failure by verify.sh.
    private static final String TEMPLATE_WITH_SKIP = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="StackTest" tests="3" failures="2" errors="0" skipped="1">
              <testcase name="push_then_pop" classname="StackTest"><failure message="x"/></testcase>
              <testcase name="size_tracks_elements" classname="StackTest"><failure message="x"/></testcase>
              <testcase name="peek_does_not_remove" classname="StackTest"><skipped/></testcase>
            </testsuite>
            """;

    /** Writes a UTF-8 text fixture via Apache {@link FileUtils} (the arch-mandated replacement for {@code Files.write*}), creating any missing parent directories. */
    private static void writeString(Path path, CharSequence content) throws IOException {
        FileUtils.writeStringToFile(path.toFile(), content.toString(), StandardCharsets.UTF_8);
    }

    /** Charset-explicit overload kept so the existing call sites that named the charset stay byte-identical. */
    private static void writeString(Path path, CharSequence content, Charset charset) throws IOException {
        FileUtils.writeStringToFile(path.toFile(), content.toString(), charset);
    }

    /**
     * PRODUCTION side: the parser the real LocalCI pipeline uses drops the skipped testcase from BOTH the successful and the failed lists. So the persisted exercise's synced test
     * case {@code peek_does_not_remove} receives no feedback on the solution build, and production grades it as not-executed (0 credit) — the solution cannot reach 100%.
     */
    @Test
    void production_TestResultXmlParser_dropsSkippedTestcaseEntirely() throws Exception {
        List<LocalCITestJobDTO> failed = new ArrayList<>();
        List<LocalCITestJobDTO> successful = new ArrayList<>();
        TestResultXmlParser.processTestResultFile(SOLUTION_WITH_SKIP, failed, successful);

        List<String> successfulNames = successful.stream().map(LocalCITestJobDTO::name).toList();
        List<String> failedNames = failed.stream().map(LocalCITestJobDTO::name).toList();

        // The two real tests are successful; the skipped one is in NEITHER list — production simply does not see it as a passing test.
        assertThat(successfulNames).containsExactlyInAnyOrder("push_then_pop", "size_tracks_elements");
        assertThat(failedNames).isEmpty();
        assertThat(successfulNames).as("production never counts the skipped test as a pass").doesNotContain("peek_does_not_remove");
        assertThat(failedNames).doesNotContain("peek_does_not_remove");
    }

    /**
     * SANDBOX side (the D1 divergence is now FIXED): the shipped {@code verify.sh} excludes a {@code <testcase>} carrying a {@code <skipped/>} child from BOTH the executed test
     * count and the emitted HYPERION_TESTNAME set, mirroring {@code TestResultXmlParser.isSkipped() → continue}. The oracle now sees solution tests=2 (the two real tests), and the
     * skipped name is in neither the solution-passing set nor the failed set — exactly like production.
     */
    @EnabledOnOs({ LINUX, MAC })
    @Test
    void sandbox_verifyScript_excludesSkippedTestcase_matchingProduction(@TempDir Path tempDir) throws Exception {
        Aggregate solution = aggregate(tempDir, "sol", SOLUTION_WITH_SKIP);
        Emitted solutionEmit = emit(tempDir, "sol-emit", SOLUTION_WITH_SKIP);
        Aggregate template = aggregate(tempDir, "tpl", TEMPLATE_WITH_SKIP);

        // verify.sh now excludes the skipped testcase from the executed count: tests=2 (the two real tests), with skipped=1 recorded separately.
        assertThat(solution.tests()).as("verify.sh excludes the skipped <testcase> from the solution test count").isEqualTo(2);
        assertThat(solution.failures()).isZero();
        assertThat(solution.errors()).isZero();
        assertThat(solution.skipped()).isEqualTo(1);

        // The skipped test's name is emitted in NEITHER the solution name set nor the failed set (production records it as neither successful nor failed).
        assertThat(solutionEmit.names()).as("the skipped test is not emitted as a solution test name").doesNotContain("peek_does_not_remove");
        assertThat(solutionEmit.names()).containsExactlyInAnyOrder("push_then_pop", "size_tracks_elements");
        assertThat(solutionEmit.failed()).doesNotContain("peek_does_not_remove");

        // Both runs exclude the same skip, so the counts still agree (no spurious "different number of tests" rejection for a symmetric skip).
        assertThat(template.tests()).isEqualTo(solution.tests());
    }

    /**
     * The divergence is CLOSED, stated as one assertion: the sandbox solution-passing name set (what the oracle treats as the 100% solution) now EQUALS the production parser's
     * solution-successful name set — neither contains the skipped {@code peek_does_not_remove}. So the persisted exercise's oracle view and the real graded view agree on which
     * tests the solution passes, removing the sandbox-accept-but-production-solution-below-100% gap for skipped tests.
     */
    @EnabledOnOs({ LINUX, MAC })
    @Test
    void parity_skippedTest_isExcludedInBothSandboxAndProduction(@TempDir Path tempDir) throws Exception {
        // Sandbox: the solution-passing names = every emitted HYPERION_TESTNAME (the solution passes them all; skipped cases are no longer emitted).
        Emitted sandboxSolution = emit(tempDir, "div-sol", SOLUTION_WITH_SKIP);
        List<String> sandboxSolutionPassing = sandboxSolution.names();

        // Production: the solution-successful names = the parser's successful list.
        List<LocalCITestJobDTO> failed = new ArrayList<>();
        List<LocalCITestJobDTO> successful = new ArrayList<>();
        TestResultXmlParser.processTestResultFile(SOLUTION_WITH_SKIP, failed, successful);
        List<String> productionSolutionSuccessful = successful.stream().map(LocalCITestJobDTO::name).toList();

        assertThat(sandboxSolutionPassing).as("sandbox no longer treats the skipped test as a solution pass").doesNotContain("peek_does_not_remove");
        assertThat(productionSolutionSuccessful).as("production also excludes the skipped test").doesNotContain("peek_does_not_remove");
        // The two views now AGREE on the same report (order-independent): the skipped-test divergence is closed.
        assertThat(sandboxSolutionPassing).containsExactlyInAnyOrderElementsOf(productionSolutionSuccessful);
    }

    // A real-shaped SpotBugs report (one bug instance) and a Checkstyle report (one error), as the SCA static phase (`mvn spotbugs:spotbugs checkstyle:checkstyle …`,
    // java/plain_maven_static.yaml) writes them. They carry NO <testcase> element, so the sandbox's JUnit-XML aggregation cannot see them — while production parses them into SCA
    // feedback and (when SCA is enabled with a penalty) subtracts a penalty from the solution score (ProgrammingExerciseGradingService.calculateTotalPenalty).
    private static final String SPOTBUGS_REPORT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <BugCollection version="4.7.3">
              <BugInstance type="DM_DEFAULT_ENCODING" priority="2" category="STYLE">
                <Class classname="de.test.Stack"/>
                <SourceLine classname="de.test.Stack" start="12" end="12"/>
              </BugInstance>
            </BugCollection>
            """;

    private static final String CHECKSTYLE_REPORT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <checkstyle version="10.3">
              <file name="Stack.java">
                <error line="3" severity="warning" message="Missing a Javadoc comment." source="com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocTypeCheck"/>
              </file>
            </checkstyle>
            """;

    /**
     * SCA divergence: a reference SOLUTION with static-analysis violations. The sandbox aggregation cannot see the SpotBugs/Checkstyle reports (no {@code <testcase>}), so the SCA
     * phase exits 0 and contributes nothing to the verdict — verify.sh accepts the solution as a full pass. Production parses these reports and, when
     * {@code staticCodeAnalysisEnabled}
     * with a penalty, deducts from the score so the solution grades below 100%. The authentic E2E test disables SCA, so it never reproduces this. Here we prove the sandbox side is
     * blind: a build dir containing ONLY the SCA reports (no JUnit XML) aggregates to zero tests, exactly as a passing solution's SCA phase would add zero to the count.
     */
    @EnabledOnOs({ LINUX, MAC })
    @Test
    void sandbox_ignoresStaticCodeAnalysisReports_whileProductionWouldPenalizeThem(@TempDir Path tempDir) throws Exception {
        Path buildDir = Files.createDirectories(tempDir.resolve("sca"));
        Path marker = buildDir.resolve(".hyperion-build-start");
        writeString(marker, "");
        Files.setLastModifiedTime(marker, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(3600)));
        // The SCA phase writes its reports under target/ (Maven) — not into any of the JUnit report-glob locations and with no <testcase> content.
        Path target = Files.createDirectories(buildDir.resolve("target"));
        writeString(target.resolve("spotbugsXml.xml"), SPOTBUGS_REPORT, StandardCharsets.UTF_8);
        writeString(target.resolve("checkstyle-result.xml"), CHECKSTYLE_REPORT, StandardCharsets.UTF_8);

        String script = "BUILD_DIR='" + buildDir + "'\nBUILD_START_MARKER='" + marker + "'\n" + aggregationSnippet()
                + "\necho \"tests=$tests failures=$failures errors=$errors skipped=$skipped\"\n";
        Path scriptFile = tempDir.resolve("sca-aggregate.sh");
        writeString(scriptFile, script, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("sca aggregation snippet did not finish in time");
        }
        // The sandbox sees zero tests/failures from the SCA reports: they are invisible to its verdict. (A real solution would also have its JUnit reports; the point is the SCA
        // violation contributes NOTHING, so it cannot pull the sandbox verdict below a full pass — but production's SCA penalty pulls the real score below 100%.)
        assertThat(output).as("verify.sh aggregation ignores SpotBugs/Checkstyle reports (no <testcase>)").contains("tests=0").contains("failures=0").contains("errors=0");
    }

    /**
     * The D2 divergence is now CLOSED for the SCA-penalising case. The aggregation still ignores SCA reports (they carry no {@code <testcase>}), but the script ADDITIONALLY emits
     * a
     * {@code HYPERION_SCA <TOOL>|<rawCategory>} line per finding, and {@link ScaPenaltyParity} (the pure replica of production's {@code calculateTotalPenalty} gating) flags
     * exactly
     * the findings production would penalise. Here the byte-identical SpotBugs/Checkstyle fixtures from the divergence repro, with a GRADED + positively-penalised category, ARE
     * flagged — so the oracle would REJECT a reference solution production would dock below 100%, instead of accepting it.
     */
    @EnabledOnOs({ LINUX, MAC })
    @Test
    void parity_scaFindings_areFlaggedWhenProductionWouldPenalise(@TempDir Path tempDir) throws Exception {
        List<String> findings = runScaSection(tempDir, "sca-closed");

        // The script now surfaces the SpotBugs and Checkstyle findings with the SAME raw category production's parsers assign (DM_DEFAULT_ENCODING => I category in our fixture;
        // the
        // Checkstyle JavadocType rule => javadoc category).
        assertThat(findings).anyMatch(f -> f.startsWith("SPOTBUGS|"));
        assertThat(findings).contains("CHECKSTYLE|javadoc");

        // Production grades the "Documentation" category (which the checkstyle 'javadoc' mapping feeds) — if an instructor marks it GRADED with a positive penalty, the solution's
        // javadoc finding is penalised. ScaPenaltyParity flags it, so the oracle rejects (divergence closed). The default category is INACTIVE, so by default nothing is flagged.
        ProgrammingExercise gradedDoc = scaExercise(50, "Documentation", CategoryState.GRADED, 1.0);
        List<String> penalising = ScaPenaltyParity.penalisingFindings(gradedDoc, gradedDoc.getStaticCodeAnalysisCategories(), findings);
        assertThat(penalising).as("the javadoc finding is penalised when Documentation is graded -> oracle rejects").contains("CHECKSTYLE|javadoc");

        // Default config (all FEEDBACK/INACTIVE): NOTHING is penalising, so the oracle's accept is unchanged — exactly matching production, which deducts no penalty without a
        // graded category.
        ProgrammingExercise defaultConfig = scaExercise(50, "Documentation", CategoryState.INACTIVE, 1.0);
        assertThat(ScaPenaltyParity.penalisingFindings(defaultConfig, defaultConfig.getStaticCodeAnalysisCategories(), findings))
                .as("no graded category => no penalty => no rejection (parity with production's default)").isEmpty();
    }

    /** A SCA-enabled Java exercise with one persisted category, used to drive ScaPenaltyParity exactly as the oracle does. */
    private static ProgrammingExercise scaExercise(int maxPenalty, String categoryName, CategoryState state, double penalty) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(99L);
        exercise.setProgrammingLanguage(de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.JAVA);
        exercise.setStaticCodeAnalysisEnabled(true);
        exercise.setMaxStaticCodeAnalysisPenalty(maxPenalty);
        var category = new de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory();
        category.setName(categoryName);
        category.setState(state);
        category.setPenalty(penalty);
        category.setMaxPenalty(penalty * 10);
        category.setProgrammingExercise(exercise);
        exercise.setStaticCodeAnalysisCategories(new java.util.HashSet<>(java.util.Set.of(category)));
        return exercise;
    }

    /** Runs the live SCA emission block (sliced from the generated SCA-enabled script) against the SpotBugs/Checkstyle fixtures and returns the emitted findings. */
    private List<String> runScaSection(Path tempDir, String name) throws Exception {
        Path buildDir = Files.createDirectories(tempDir.resolve(name));
        Path marker = buildDir.resolve(".hyperion-build-start");
        writeString(marker, "");
        Files.setLastModifiedTime(marker, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(3600)));
        writeString(buildDir.resolve("spotbugsXml.xml"), SPOTBUGS_REPORT, StandardCharsets.UTF_8);
        writeString(buildDir.resolve("checkstyle-result.xml"), CHECKSTYLE_REPORT, StandardCharsets.UTF_8);

        BuildPhasesTemplateService phases = org.mockito.Mockito.mock(BuildPhasesTemplateService.class);
        org.mockito.Mockito.when(phases.getDefaultBuildPlanPhasesFor(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO("build", "echo build", null, false, List.of())));
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.JAVA);
        exercise.setStaticCodeAnalysisEnabled(true);
        String fullScript = new SandboxBuildCommandService(Optional.of(phases), Optional.of(new de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService()))
                .verifyScriptContent(exercise);
        int start = fullScript.indexOf("emit_sca_for() {");
        int end = fullScript.indexOf("echo \"" + "HYPERION_RESULT", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
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

    // A TEMPLATE build where the test that was SKIPPED on the solution instead FAILS on the template (the student's missing implementation makes the assumption/precondition no
    // longer
    // skip, or the test is simply not skipped here). This is the decisive ACCEPT-path scenario: because peek_does_not_remove is now in the template's FAILED set, the oracle's
    // production-parity gate ("every solution-passing test must fail on the template") does NOT flag it, and the suite is accepted — yet on the SOLUTION it was skipped, so
    // production
    // grades it as not-executed and the solution scores below 100%.
    private static final String TEMPLATE_SKIPPED_TEST_FAILS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="StackTest" tests="3" failures="3" errors="0" skipped="0">
              <testcase name="push_then_pop" classname="StackTest"><failure message="x"/></testcase>
              <testcase name="size_tracks_elements" classname="StackTest"><failure message="x"/></testcase>
              <testcase name="peek_does_not_remove" classname="StackTest"><failure message="x"/></testcase>
            </testsuite>
            """;

    /**
     * The decisive proof that the divergence is now CLOSED. The dangerous case is a test SKIPPED on the solution but FAILING on the template: production grades the
     * skipped-on-solution
     * test as not-executed (0 credit), so the persisted solution would land below 100%. With the fix, the live {@code verify.sh} excludes the skipped case from the SOLUTION test
     * count and names, while on the template that same test is a real failing testcase — so the solution runs FEWER tests (2) than the template (3), and the oracle's "template
     * runs a
     * different number of tests" gate ({@code AuthoritativeVerificationService}, the {@code template.tests() != solution.tests()} check) rejects the exercise instead of accepting
     * it.
     */
    @EnabledOnOs({ LINUX, MAC })
    @Test
    void divergence_isClosed_oracleRejectsWhenSkippedSolutionTestFailsOnTemplate(@TempDir Path tempDir) throws Exception {
        // Build the solution/template counts and name sets exactly as the live verify.sh emits them.
        Aggregate solution = aggregate(tempDir, "acc-sol", SOLUTION_WITH_SKIP);
        Emitted solutionEmit = emit(tempDir, "acc-sol-emit", SOLUTION_WITH_SKIP);
        Aggregate template = aggregate(tempDir, "acc-tpl", TEMPLATE_SKIPPED_TEST_FAILS);

        // The skipped-on-solution test is no longer counted or named as a solution test (production parity).
        assertThat(solutionEmit.names()).as("the skipped-on-solution test is excluded from the solution name set").doesNotContain("peek_does_not_remove");
        assertThat(solution.tests()).as("solution runs only the 2 executed tests").isEqualTo(2);

        // On the template that test FAILS (it is a real testcase there), so the template runs 3 tests. The counts now differ, which is exactly the oracle's reject condition
        // (template.tests() != solution.tests()), closing the sandbox-accept-but-production-solution-below-100% path.
        assertThat(template.tests()).as("the template runs the previously-skipped test as a real failing test").isEqualTo(3);
        assertThat(solution.tests()).as("solution and template now report a DIFFERENT number of tests -> the oracle rejects").isNotEqualTo(template.tests());
    }

    // ---- Live verify.sh snippet drivers (sliced from the shipped script, run under real sh; identical technique to SandboxBuildCommandServiceTest)
    // -------------------------------

    private record Aggregate(int tests, int failures, int errors, int skipped) {
    }

    private Aggregate aggregate(Path tempDir, String name, String reportXml) throws Exception {
        Path buildDir = Files.createDirectories(tempDir.resolve(name));
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

    private String aggregationSnippet() {
        String fullScript = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
        int start = fullScript.indexOf("xml=$(find");
        int end = fullScript.indexOf("errors=$(sum_attr errors)");
        assertThat(start).isNotNegative();
        assertThat(end).isGreaterThan(start);
        return fullScript.substring(start, end + "errors=$(sum_attr errors)".length());
    }

    private record Emitted(List<String> names, List<String> failed) {
    }

    private Emitted emit(Path tempDir, String name, String reportXml) throws Exception {
        Path reportDir = Files.createDirectories(tempDir.resolve(name).resolve("test-results"));
        Path report = reportDir.resolve("results.xml");
        writeString(report, reportXml, StandardCharsets.UTF_8);

        String script = "MARK_SUFFIX=''\nxml='" + report + "'\n" + emitSnippet() + "\n";
        Path scriptFile = tempDir.resolve(name + "-emit.sh");
        writeString(scriptFile, script, StandardCharsets.UTF_8);

        Process process = new ProcessBuilder("sh", scriptFile.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("emit snippet did not finish in time");
        }
        List<String> names = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String line : output.split("\n")) {
            if (line.startsWith("HYPERION_TESTNAME ")) {
                names.add(line.substring("HYPERION_TESTNAME ".length()).trim());
            }
            else if (line.startsWith("HYPERION_TESTFAIL ")) {
                failed.add(line.substring("HYPERION_TESTFAIL ".length()).trim());
            }
        }
        return new Emitted(names, failed);
    }

    private String emitSnippet() {
        String fullScript = new SandboxBuildCommandService(Optional.empty(), Optional.empty()).verifyScriptContent(new ProgrammingExercise());
        int start = fullScript.indexOf("emit_test_lines() {");
        assertThat(start).isNotNegative();
        int call = fullScript.indexOf("\nemit_test_lines\n", start);
        assertThat(call).isGreaterThan(start);
        return fullScript.substring(start, call + "\nemit_test_lines\n".length());
    }
}
