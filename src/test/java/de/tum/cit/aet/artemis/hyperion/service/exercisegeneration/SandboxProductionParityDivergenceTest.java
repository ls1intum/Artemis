package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.localci.service.scaparser.ReportParser;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

/**
 * Parity probe: does the sandbox verdict track PRODUCTION grading semantics on byte-identical reports?
 * <p>
 * After the refactor this is parity <em>by construction</em>: the verifier collects the report files the live {@code verify.sh} writes and parses them with the SAME production
 * code
 * the real LocalCI pipeline uses ({@link TestResultXmlParser} for JUnit, {@link ReportParser} for SCA). There is no separate shell parser to drift. This test pins that by driving
 * the LIVE collect step under a real POSIX {@code sh} and feeding the collected reports into the production parsers, for the two historically-divergent shapes:
 * <ul>
 * <li><b>SKIPPED tests</b> — production drops a {@code <testcase><skipped/></testcase>} (graded as not-executed). Because the verifier now uses {@code TestResultXmlParser} on the
 * collected report, the skip is dropped identically; a test skipped on the solution but failing on the template makes the solution run fewer tests than the template, which the
 * oracle's count gate rejects.</li>
 * <li><b>SCA findings</b> — the SCA reports carry no {@code <testcase>}, so they are invisible to the JUnit differential, but the verifier collects them and
 * {@link ScaPenaltyParity}
 * flags exactly the findings production's {@code calculateTotalPenalty} would penalise, using the REAL derived category from {@link ReportParser}.</li>
 * </ul>
 */
class SandboxProductionParityDivergenceTest {

    private static final String SOLUTION_WITH_SKIP = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="StackTest" tests="3" failures="0" errors="0" skipped="1">
              <testcase name="push_then_pop" classname="StackTest"/>
              <testcase name="size_tracks_elements" classname="StackTest"/>
              <testcase name="peek_does_not_remove" classname="StackTest"><skipped/></testcase>
            </testsuite>
            """;

    private static SandboxBuildCommandService factory() {
        BuildPhasesTemplateService phases = mock(BuildPhasesTemplateService.class);
        when(phases.getDefaultBuildPlanPhasesFor(any())).thenReturn(List.of(new BuildPhaseDTO("test", "echo run", null, false, List.of())));
        return new SandboxBuildCommandService(Optional.of(phases), Optional.of(new BuildScriptProviderService()));
    }

    /**
     * Production drops the skipped testcase from BOTH lists — and so does the verifier, because it parses the collected report with the very same {@link TestResultXmlParser}. We
     * collect the report through the LIVE script and parse the collected bytes, proving the skip is excluded by construction (no shell re-implementation to diverge).
     */
    @EnabledOnOs({ LINUX, MAC })
    @Test
    void collectedSkippedReport_isParsedByProductionParser_excludingTheSkip(@TempDir Path tempDir) throws Exception {
        Map<String, String> collected = VerifyScriptTestHarness.collect(factory(), new ProgrammingExercise(), tempDir, "skip",
                Map.of("test-results/results.xml", SOLUTION_WITH_SKIP));
        assertThat(collected).hasSize(1);

        List<LocalCITestJobDTO> failed = new ArrayList<>();
        List<LocalCITestJobDTO> successful = new ArrayList<>();
        TestResultXmlParser.processTestResultFile(collected.values().iterator().next(), failed, successful);

        List<String> successfulNames = successful.stream().map(LocalCITestJobDTO::name).toList();
        assertThat(successfulNames).as("the two real tests pass; the skipped one is excluded exactly as production grades").containsExactlyInAnyOrder("push_then_pop",
                "size_tracks_elements");
        assertThat(failed).isEmpty();
        assertThat(successfulNames).doesNotContain("peek_does_not_remove");
    }

    private static final String SPOTBUGS_REPORT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <BugCollection version="4.7.3">
              <Project><SrcDir>src</SrcDir></Project>
              <BugInstance type="DM_DEFAULT_ENCODING" priority="2" category="STYLE">
                <Class classname="de.test.Stack"/>
                <SourceLine classname="de.test.Stack" sourcepath="de/test/Stack.java" start="12" end="12"/>
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
     * The SCA reports carry no {@code <testcase>}, so the JUnit differential is blind to them — but the verifier collects them and parses them with the production
     * {@link ReportParser}, which derives the SAME categories production grades. {@link ScaPenaltyParity} then flags exactly what production's {@code calculateTotalPenalty} would
     * penalise: the Checkstyle javadoc finding is penalised iff the "Documentation" category is GRADED with a positive penalty (default INACTIVE => nothing flagged, matching
     * production).
     */
    @EnabledOnOs({ LINUX, MAC })
    @Test
    void collectedScaReports_areParsedByProductionParser_andFlaggedOnlyWhenProductionWouldPenalise(@TempDir Path tempDir) throws Exception {
        Map<String, String> collected = VerifyScriptTestHarness.collect(factory(), scaJavaExercise(), tempDir, "sca",
                Map.of("test-results/results.xml", "<testsuite name=\"T\" tests=\"0\"/>", "spotbugsXml.xml", SPOTBUGS_REPORT, "checkstyle-result.xml", CHECKSTYLE_REPORT));

        // The two SCA reports were collected under their canonical names; parse them with production's ReportParser to get the real derived categories.
        List<ScaPenaltyParity.ScaFinding> findings = parseCollectedSca(collected);
        assertThat(findings).anyMatch(f -> "SPOTBUGS".equals(f.tool()));
        assertThat(findings).anyMatch(f -> "CHECKSTYLE".equals(f.tool()) && "javadoc".equals(f.category()));

        // Documentation graded with a positive penalty => the javadoc finding is penalised => the oracle would reject.
        ProgrammingExercise gradedDoc = scaExercise(50, "Documentation", CategoryState.GRADED, 1.0);
        assertThat(ScaPenaltyParity.penalisingFindings(gradedDoc, gradedDoc.getStaticCodeAnalysisCategories(), findings))
                .as("the javadoc finding is penalised when Documentation is graded -> oracle rejects").anyMatch(f -> "CHECKSTYLE".equals(f.tool()));

        // Default config (INACTIVE): nothing penalising => accept unchanged, matching production's no-penalty default.
        ProgrammingExercise defaultConfig = scaExercise(50, "Documentation", CategoryState.INACTIVE, 1.0);
        assertThat(ScaPenaltyParity.penalisingFindings(defaultConfig, defaultConfig.getStaticCodeAnalysisCategories(), findings))
                .as("no graded category => no penalty => no rejection (parity with production's default)").isEmpty();
    }

    /** Parses each collected SCA report (keyed by {@code <seq>__<canonical>}) with production's {@link ReportParser}, returning the tool + real derived category per issue. */
    private static List<ScaPenaltyParity.ScaFinding> parseCollectedSca(Map<String, String> collected) {
        List<ScaPenaltyParity.ScaFinding> findings = new ArrayList<>();
        for (Map.Entry<String, String> entry : collected.entrySet()) {
            int sep = entry.getKey().indexOf(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR);
            String canonical = sep < 0 ? entry.getKey() : entry.getKey().substring(sep + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR.length());
            if (canonical.equals(SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN)) {
                continue;
            }
            StaticCodeAnalysisReportDTO report = ReportParser.getReport(entry.getValue(), canonical);
            for (StaticCodeAnalysisIssue issue : report.issues()) {
                findings.add(new ScaPenaltyParity.ScaFinding(report.tool().name(), issue.category()));
            }
        }
        return findings;
    }

    private static final String GCC_REPORT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>main.c:10:5: warning: unused variable 'x' [-Wunused-variable]
            </root>
            """;

    /**
     * Proves the production category derivation is now used for GCC (and, by the same {@code ReportParser} path, SARIF): the {@code -Wunused-variable} warning derives the concrete
     * category {@code BadPractice} (mapping to the "Bad Practice" default category), NOT the old {@code *} sentinel. So {@link ScaPenaltyParity} penalises it iff "Bad Practice" is
     * graded — and a finding in a DIFFERENT graded category ("Security") is NOT penalised, where the old conservative {@code <tool>|*} would have over-rejected it.
     */
    @Test
    void gccCategoryIsDerivedByProduction_soANonMatchingGradedCategoryDoesNotPenalise() {
        StaticCodeAnalysisReportDTO report = ReportParser.getReport(GCC_REPORT, "gcc.xml");
        assertThat(report.issues()).as("a concrete GCC finding is parsed").hasSize(1);
        String derivedCategory = report.issues().getFirst().category();
        assertThat(derivedCategory).as("the real derived category is used, not the old * sentinel").isEqualTo("BadPractice").isNotEqualTo("*");
        List<ScaPenaltyParity.ScaFinding> findings = List.of(new ScaPenaltyParity.ScaFinding(report.tool().name(), derivedCategory));

        // "Bad Practice" graded (the default category the BadPractice GCC finding maps to) => penalised.
        ProgrammingExercise badPracticeGraded = scaCExercise("Bad Practice", CategoryState.GRADED, 1.0);
        assertThat(ScaPenaltyParity.penalisingFindings(badPracticeGraded, badPracticeGraded.getStaticCodeAnalysisCategories(), findings))
                .as("a GCC finding in a graded matching category is penalised").hasSize(1);

        // "Security" graded but the finding's derived category is "BadPractice" => NOT penalised (the old <tool>|* sentinel would have flagged it conservatively).
        ProgrammingExercise securityGraded = scaCExercise("Security", CategoryState.GRADED, 1.0);
        assertThat(ScaPenaltyParity.penalisingFindings(securityGraded, securityGraded.getStaticCodeAnalysisCategories(), findings))
                .as("a GCC finding whose derived category is not the graded one must NOT penalise (real derivation, not <tool>|*)").isEmpty();
    }

    /** A SCA-enabled C exercise with one persisted GCC category whose name is a default GCC category ("Bad Practice"/"Security"), graded with the given state/penalty. */
    private static ProgrammingExercise scaCExercise(String categoryName, CategoryState state, double penalty) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(101L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.C);
        exercise.setStaticCodeAnalysisEnabled(true);
        exercise.setMaxStaticCodeAnalysisPenalty(50);
        var category = new StaticCodeAnalysisCategory();
        category.setName(categoryName);
        category.setState(state);
        category.setPenalty(penalty);
        category.setMaxPenalty(penalty * 10);
        category.setProgrammingExercise(exercise);
        exercise.setStaticCodeAnalysisCategories(new HashSet<>(Set.of(category)));
        return exercise;
    }

    private static ProgrammingExercise scaJavaExercise() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setStaticCodeAnalysisEnabled(true);
        return exercise;
    }

    private static ProgrammingExercise scaExercise(int maxPenalty, String categoryName, CategoryState state, double penalty) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(99L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setStaticCodeAnalysisEnabled(true);
        exercise.setMaxStaticCodeAnalysisPenalty(maxPenalty);
        var category = new StaticCodeAnalysisCategory();
        category.setName(categoryName);
        category.setState(state);
        category.setPenalty(penalty);
        category.setMaxPenalty(penalty * 10);
        category.setProgrammingExercise(exercise);
        exercise.setStaticCodeAnalysisCategories(new HashSet<>(Set.of(category)));
        return exercise;
    }
}
