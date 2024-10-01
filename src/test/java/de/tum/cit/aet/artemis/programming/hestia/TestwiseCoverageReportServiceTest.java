package de.tum.cit.aet.artemis.programming.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.hestia.TestwiseCoverageReportEntry;
import de.tum.cit.aet.artemis.programming.hestia.util.HestiaUtilTestService;
import de.tum.cit.aet.artemis.programming.hestia.util.TestwiseCoverageTestUtil;
import de.tum.cit.aet.artemis.programming.localvcci.AbstractLocalCILocalVCIntegrationTest;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.CoverageReportRepository;
import de.tum.cit.aet.artemis.programming.service.hestia.TestwiseCoverageService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

class TestwiseCoverageReportServiceTest extends AbstractLocalCILocalVCIntegrationTest {

    private static final String TEST_PREFIX = "testwisecoveragereportservice";

    @Autowired
    private TestwiseCoverageService testwiseCoverageService;

    @Autowired
    private CoverageReportRepository coverageReportRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseTestRepository programmingExerciseTestCaseRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    private HestiaUtilTestService hestiaUtilTestService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission solutionSubmission;

    private final LocalRepository solutionRepo = new LocalRepository("main");

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void setup() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, true, ProgrammingLanguage.JAVA);
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        programmingExercise = hestiaUtilTestService.setupSolution(
                Map.ofEntries(Map.entry("src/de/tum/in/ase/BubbleSort.java", "\n ".repeat(28)), Map.entry("src/de/tum/in/ase/Context.java", "\n ".repeat(18))), programmingExercise,
                solutionRepo);

        var testCase1 = new ProgrammingExerciseTestCase().testName("test1()").exercise(programmingExercise).active(true).weight(1.0);
        programmingExerciseTestCaseRepository.save(testCase1);
        var testCase2 = new ProgrammingExerciseTestCase().testName("test2()").exercise(programmingExercise).active(true).weight(1.0);
        programmingExerciseTestCaseRepository.save(testCase2);
        var solutionParticipation = solutionProgrammingExerciseRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        solutionSubmission = programmingExerciseUtilService.createProgrammingSubmission(solutionParticipation, false);
        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());
    }

    @AfterEach
    void cleanup() throws IOException {
        solutionRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCreateFullTestwiseCoverageReport() {
        var fileReportsByTestName = TestwiseCoverageTestUtil.generateCoverageFileReportByTestName();
        testwiseCoverageService.createTestwiseCoverageReport(fileReportsByTestName, programmingExercise, solutionSubmission);

        var reports = coverageReportRepository.getLatestCoverageReportsWithLegalSubmissionsForProgrammingExercise(programmingExercise.getId(), Pageable.ofSize(1));
        assertThat(reports).hasSize(1);
        var report = reports.getFirst();
        // 18/50 lines covered = 32%
        assertThat(report.getCoveredLineRatio()).isEqualTo(0.32);

        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        var testCase1 = testCases.stream().filter(testCase -> "test1()".equals(testCase.getTestName())).findFirst().orElseThrow();
        var testCase2 = testCases.stream().filter(testCase -> "test2()".equals(testCase.getTestName())).findFirst().orElseThrow();

        var optionalFullReportWithFileReports = coverageReportRepository.findCoverageReportByIdWithEagerFileReportsAndEntries(report.getId());
        assertThat(optionalFullReportWithFileReports).isPresent();
        var fullReportWithFileReports = optionalFullReportWithFileReports.get();
        var fileReports = fullReportWithFileReports.getFileReports();
        assertThat(fileReports).hasSize(2);

        var bubbleSortFileReport = fileReports.stream().filter(fileReport -> "src/de/tum/in/ase/BubbleSort.java".equals(fileReport.getFilePath())).findFirst().orElseThrow();
        var entriesBubbleSort = bubbleSortFileReport.getTestwiseCoverageEntries();
        assertThat(entriesBubbleSort).hasSize(4);
        checkIfSetContainsEntry(entriesBubbleSort, 15, 3, testCase1);
        checkIfSetContainsEntry(entriesBubbleSort, 23, 1, testCase1);
        checkIfSetContainsEntry(entriesBubbleSort, 2, 1, testCase2);
        checkIfSetContainsEntry(entriesBubbleSort, 16, 3, testCase2);

        var contextFileReport = fileReports.stream().filter(fileReport -> "src/de/tum/in/ase/Context.java".equals(fileReport.getFilePath())).findFirst().orElseThrow();
        assertThat(contextFileReport.getTestwiseCoverageEntries()).hasSize(1);
        checkIfSetContainsEntry(contextFileReport.getTestwiseCoverageEntries(), 1, 10, testCase2);
    }

    private void checkIfSetContainsEntry(Set<TestwiseCoverageReportEntry> set, Integer startLine, Integer lineCount, ProgrammingExerciseTestCase testCase) {
        assertThat(set).anyMatch(entry -> startLine.equals(entry.getStartLine()) && lineCount.equals(entry.getLineCount()) && testCase.equals(entry.getTestCase()));
    }
}
