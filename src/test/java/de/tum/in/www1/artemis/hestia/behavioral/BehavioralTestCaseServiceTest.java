package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.*;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralTestCaseService;
import de.tum.in.www1.artemis.util.HestiaUtilTestService;
import de.tum.in.www1.artemis.util.LocalRepository;

public class BehavioralTestCaseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final LocalRepository solutionRepo = new LocalRepository("main");

    @Autowired
    private BehavioralTestCaseService behavioralTestCaseService;

    @Autowired
    private HestiaUtilTestService hestiaUtilTestService;

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    private CoverageReportRepository coverageReportRepository;

    @Autowired
    private CoverageFileReportRepository coverageFileReportRepository;

    @Autowired
    private TestwiseCoverageReportEntryRepository testwiseCoverageReportEntryRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(0, 0, 0, 1);
        database.addCourseWithOneProgrammingExercise(false, true, ProgrammingLanguage.JAVA);
        exercise = programmingExerciseRepository.findAll().get(0);
        exercise.setTestwiseCoverageEnabled(true);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    private ProgrammingExerciseTestCase addTestCaseToExercise(String name) {
        var testCase = new ProgrammingExerciseTestCase();
        testCase.setTestName(name);
        testCase.setExercise(exercise);
        testCase.setVisibility(Visibility.ALWAYS);
        testCase.setActive(true);
        testCase.setWeight(1D);
        testCase.setType(ProgrammingExerciseTestCaseType.BEHAVIORAL);
        return testCaseRepository.save(testCase);
    }

    private ProgrammingExerciseGitDiffReport newGitDiffReport() {
        var gitDiffReport = new ProgrammingExerciseGitDiffReport();
        gitDiffReport.setEntries(new HashSet<>());
        gitDiffReport.setProgrammingExercise(exercise);
        gitDiffReport.setSolutionRepositoryCommitHash("123a");
        gitDiffReport.setTemplateRepositoryCommitHash("123b");
        gitDiffReport = programmingExerciseGitDiffReportRepository.save(gitDiffReport);
        return gitDiffReport;
    }

    private ProgrammingExerciseGitDiffReport addGitDiffEntry(String filePath, int startLine, int lineCount, ProgrammingExerciseGitDiffReport gitDiffReport) {
        var gitDiffEntry = new ProgrammingExerciseGitDiffEntry();
        gitDiffEntry.setFilePath(filePath);
        gitDiffEntry.setStartLine(startLine);
        gitDiffEntry.setLineCount(lineCount);
        gitDiffEntry.setGitDiffReport(gitDiffReport);
        gitDiffReport.getEntries().add(gitDiffEntry);
        var savedGitDiffReport = programmingExerciseGitDiffReportRepository.save(gitDiffReport);
        return savedGitDiffReport;
    }

    private CoverageReport newCoverageReport() {
        var solutionParticipation = solutionProgrammingExerciseRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exercise.getId()).orElseThrow();
        var solutionSubmission = database.createProgrammingSubmission(solutionParticipation, false);

        var coverageReport = new CoverageReport();
        coverageReport.setFileReports(new HashSet<>());
        coverageReport.setSubmission(solutionSubmission);
        coverageReport = coverageReportRepository.save(coverageReport);
        return coverageReport;
    }

    private CoverageFileReport newCoverageFileReport(String filePath, CoverageReport coverageReport) {
        var coverageFileReport = new CoverageFileReport();
        coverageFileReport.setFilePath(filePath);
        coverageFileReport.setTestwiseCoverageEntries(new HashSet<>());
        coverageFileReport.setFullReport(coverageReport);
        coverageFileReport = coverageFileReportRepository.save(coverageFileReport);
        coverageReport.getFileReports().add(coverageFileReport);
        return coverageFileReport;
    }

    private TestwiseCoverageReportEntry newCoverageReportEntry(int startLine, int lineCount, ProgrammingExerciseTestCase testCase, CoverageFileReport coverageFileReport) {
        var coverageReportEntry = new TestwiseCoverageReportEntry();
        coverageReportEntry.setTestCase(testCase);
        coverageReportEntry.setStartLine(startLine);
        coverageReportEntry.setLineCount(lineCount);
        coverageReportEntry.setFileReport(coverageFileReport);
        coverageReportEntry = testwiseCoverageReportEntryRepository.save(coverageReportEntry);
        coverageFileReport.getTestwiseCoverageEntries().add(coverageReportEntry);
        return coverageReportEntry;
    }

    @Test
    @Timeout(1000)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGenerationForSimpleExample() throws Exception {
        exercise = hestiaUtilTestService.setupSolution("Test.java", "A\nB\nC\nD\nE\nF\nG\nH", exercise, solutionRepo);
        var testCase = addTestCaseToExercise("testCase");

        var gitDiffReport = newGitDiffReport();
        addGitDiffEntry("Test.java", 2, 7, gitDiffReport);

        var coverageReport = newCoverageReport();
        var coverageFileReport = newCoverageFileReport("Test.java", coverageReport);
        newCoverageReportEntry(1, 3, testCase, coverageFileReport);
        newCoverageReportEntry(5, 2, testCase, coverageFileReport);

        var solutionEntries = behavioralTestCaseService.generateBehavioralSolutionEntries(exercise);

        var expected1 = new ProgrammingExerciseSolutionEntry();
        expected1.setId(0L);
        expected1.setFilePath("Test.java");
        expected1.setTestCase(testCase);
        expected1.setLine(2);
        expected1.setCode("B\nC");
        var expected2 = new ProgrammingExerciseSolutionEntry();
        expected2.setId(0L);
        expected2.setFilePath("Test.java");
        expected2.setTestCase(testCase);
        expected2.setLine(5);
        expected2.setCode("E\nF");
        assertThat(solutionEntries).isNotNull();
        solutionEntries.forEach(solutionEntry -> solutionEntry.setId(0L));
        assertThat(solutionEntries).containsExactlyInAnyOrder(expected1, expected2);
    }
}
