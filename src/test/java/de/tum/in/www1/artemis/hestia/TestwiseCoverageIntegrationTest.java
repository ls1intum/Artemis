package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.hestia.CoverageFileReport;
import de.tum.in.www1.artemis.domain.hestia.CoverageReport;
import de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.CoverageFileReportRepository;
import de.tum.in.www1.artemis.repository.hestia.CoverageReportRepository;
import de.tum.in.www1.artemis.repository.hestia.TestwiseCoverageReportEntryRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

class TestwiseCoverageIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "testwisecoverageint";

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private CoverageReportRepository coverageReportRepository;

    @Autowired
    private CoverageFileReportRepository coverageFileReportRepository;

    @Autowired
    private TestwiseCoverageReportEntryRepository testwiseCoverageReportEntryRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission latestSolutionSubmission;

    private CoverageReport latestReport;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 0);
        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, true, ProgrammingLanguage.JAVA);
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        var solutionParticipation = solutionProgrammingExerciseRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        var unsavedPreviousSubmission = new ProgrammingSubmission();
        unsavedPreviousSubmission.setParticipation(solutionParticipation);
        unsavedPreviousSubmission.setSubmissionDate(ZonedDateTime.of(2022, 4, 5, 12, 0, 0, 0, ZoneId.of("Europe/Berlin")));
        var previousSolutionSubmission = programmingSubmissionRepository.save(unsavedPreviousSubmission);
        var unsavedLatestSubmission = new ProgrammingSubmission();
        unsavedLatestSubmission.setParticipation(solutionParticipation);
        unsavedLatestSubmission.setSubmissionDate(ZonedDateTime.of(2022, 4, 5, 13, 0, 0, 0, ZoneId.of("Europe/Berlin")));
        latestSolutionSubmission = programmingSubmissionRepository.save(unsavedLatestSubmission);

        var testCase1 = programmingExerciseTestCaseRepository.save(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("test1()"));
        var testCase2 = programmingExerciseTestCaseRepository.save(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("test2()"));
        generateAndSaveSimpleReport(0.3, "src/de/tum/in/ase/BubbleSort.java", 15, 5, 1, 5, testCase1, previousSolutionSubmission);
        latestReport = generateAndSaveSimpleReport(0.4, "src/de/tum/in/ase/BubbleSort.java", 20, 8, 1, 8, testCase2, latestSolutionSubmission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLatestFullCoverageReportAsStudent() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/full-testwise-coverage-report", HttpStatus.FORBIDDEN, CoverageReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getLatestFullCoverageReportAsTutor() throws Exception {
        var fullReport = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/full-testwise-coverage-report", HttpStatus.OK, CoverageReport.class);
        assertThat(fullReport.getCoveredLineRatio()).isEqualTo(latestReport.getCoveredLineRatio());
        var fileReports = fullReport.getFileReports();
        assertThat(fileReports).hasSize(1);
        var fileReport = fileReports.stream().toList().get(0);
        assertThat(fileReport.getFilePath()).isEqualTo("src/de/tum/in/ase/BubbleSort.java");
        assertThat(fileReport.getCoveredLineCount()).isEqualTo(8);
        assertThat(fileReport.getLineCount()).isEqualTo(20);
        // the latest coverage report only contains one file report
        assertThat(fileReport.getTestwiseCoverageEntries())
                .containsExactlyElementsOf(latestReport.getFileReports().stream().flatMap(report -> report.getTestwiseCoverageEntries().stream()).toList());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLatestCoverageReportAsStudent() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/testwise-coverage-report", HttpStatus.FORBIDDEN, CoverageReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getLatestCoverageReportAsTutor() throws Exception {
        var report = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/testwise-coverage-report", HttpStatus.OK, CoverageReport.class);
        assertThat(report.getFileReports()).isNull();
        assertThat(report.getCoveredLineRatio()).isEqualTo(0.4);
    }

    private CoverageReport generateAndSaveSimpleReport(double coveredLineRatio, String filePath, Integer fileLineCount, Integer coveredLineCount, Integer startLine,
            Integer lineCount, ProgrammingExerciseTestCase testCase, ProgrammingSubmission submission) {
        var unsavedLatestReport = new CoverageReport();
        unsavedLatestReport.setSubmission(latestSolutionSubmission);
        unsavedLatestReport.setCoveredLineRatio(coveredLineRatio);
        unsavedLatestReport.setSubmission(submission);
        var resultReport = coverageReportRepository.save(unsavedLatestReport);

        var unsavedLatestFileReport = new CoverageFileReport();
        unsavedLatestFileReport.setFilePath(filePath);
        unsavedLatestFileReport.setLineCount(fileLineCount);
        unsavedLatestFileReport.setCoveredLineCount(coveredLineCount);
        unsavedLatestFileReport.setFullReport(unsavedLatestReport);
        coverageFileReportRepository.save(unsavedLatestFileReport);

        var unsavedLatestEntry = new TestwiseCoverageReportEntry();
        unsavedLatestEntry.setStartLine(startLine);
        unsavedLatestEntry.setLineCount(lineCount);
        unsavedLatestEntry.setTestCase(testCase);
        unsavedLatestEntry.setFileReport(unsavedLatestFileReport);
        testwiseCoverageReportEntryRepository.save(unsavedLatestEntry);

        return coverageReportRepository.findCoverageReportByIdWithEagerFileReportsAndEntriesElseThrow(resultReport.getId());
    }
}
