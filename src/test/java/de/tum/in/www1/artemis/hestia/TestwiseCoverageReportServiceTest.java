package de.tum.in.www1.artemis.hestia;

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

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.localvcci.AbstractLocalCILocalVCIntegrationTest;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.CoverageReportRepository;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.HestiaUtilTestService;
import de.tum.in.www1.artemis.util.LocalRepository;

class TestwiseCoverageReportServiceTest extends AbstractLocalCILocalVCIntegrationTest {

    private static final String TEST_PREFIX = "testwisecoveragereportservice";

    @Autowired
    private TestwiseCoverageService testwiseCoverageService;

    @Autowired
    private CoverageReportRepository coverageReportRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

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

        var reports = coverageReportRepository.getLatestCoverageReportsForLegalSubmissionsForProgrammingExercise(programmingExercise.getId(), Pageable.ofSize(1));
        assertThat(reports).hasSize(1);
        var report = reports.get(0);
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
