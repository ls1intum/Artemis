package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseGitDiffReportRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.in.www1.artemis.util.HestiaUtilTestService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.web.rest.dto.hestia.ProgrammingExerciseFullGitDiffEntryDTO;

/**
 * Tests for the ProgrammingExerciseGitDiffReportService
 */
class ProgrammingExerciseGitDiffReportServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final static String FILE_NAME = "test.java";

    private final LocalRepository solutionRepo = new LocalRepository("main");

    private final LocalRepository templateRepo = new LocalRepository("main");

    private ProgrammingExercise exercise;

    @Autowired
    private HestiaUtilTestService hestiaUtilTestService;

    @Autowired
    private ProgrammingExerciseGitDiffReportService reportService;

    @Autowired
    private ProgrammingExerciseGitDiffReportRepository reportRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(1, 1, 1, 1);
        database.addCourseWithOneProgrammingExercise();
        exercise = programmingExerciseRepository.findAll().get(0);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getFullDiffNonExistingDiffShouldGenerateDiff() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "Line 1\nLine 2", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "Line 1\nLine 2", exercise, solutionRepo);
        var report = reportService.getFullReport(exercise);
        assertThat(report).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateGitDiffNoChanges() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "Line 1\nLine 2", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "Line 1\nLine 2", exercise, solutionRepo);
        var report = reportService.updateReport(exercise);
        assertThat(report.getEntries()).isNullOrEmpty();

        var fullReport = reportService.getFullReport(exercise);
        assertThat(fullReport.getEntries()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateGitDiffAppendLine1() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "Line 1\nLine 2", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "Line 1\nLine 2\nLine 3\n", exercise, solutionRepo);
        var report = reportService.updateReport(exercise);
        assertThat(report.getEntries()).hasSize(1);
        var entry = report.getEntries().stream().findFirst().orElseThrow();
        assertThat(entry.getPreviousStartLine()).isEqualTo(2);
        assertThat(entry.getStartLine()).isEqualTo(2);
        assertThat(entry.getPreviousLineCount()).isEqualTo(1);
        assertThat(entry.getLineCount()).isEqualTo(2);

        var fullReport = reportService.getFullReport(exercise);
        assertThat(fullReport.getEntries()).hasSize(1);
        var fullEntry = fullReport.getEntries().stream().findFirst().orElseThrow();
        assertThat(fullEntry.getPreviousLine()).isEqualTo(2);
        assertThat(fullEntry.getLine()).isEqualTo(2);
        assertThat(fullEntry.getPreviousCode()).isEqualTo("Line 2");
        assertThat(fullEntry.getCode()).isEqualTo("Line 2\nLine 3");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateGitDiffAppendLine2() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "Line 1\nLine 2\n", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "Line 1\nLine 2\nLine 3\n", exercise, solutionRepo);
        var report = reportService.updateReport(exercise);
        assertThat(report.getEntries()).hasSize(1);
        var entry = report.getEntries().stream().findFirst().orElseThrow();
        assertThat(entry.getPreviousStartLine()).isNull();
        assertThat(entry.getStartLine()).isEqualTo(3);
        assertThat(entry.getPreviousLineCount()).isNull();
        assertThat(entry.getLineCount()).isEqualTo(1);

        var fullReport = reportService.getFullReport(exercise);
        assertThat(fullReport.getEntries()).hasSize(1);
        var fullEntry = fullReport.getEntries().stream().findFirst().orElseThrow();
        assertThat(fullEntry.getPreviousLine()).isNull();
        assertThat(fullEntry.getLine()).isEqualTo(3);
        assertThat(fullEntry.getPreviousCode()).isNull();
        assertThat(fullEntry.getCode()).isEqualTo("Line 3");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateGitDiffAddToEmptyFile() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "Line 1\nLine 2", exercise, solutionRepo);
        var report = reportService.updateReport(exercise);
        assertThat(report.getEntries()).hasSize(1);
        var entry = report.getEntries().stream().findFirst().orElseThrow();
        assertThat(entry.getPreviousStartLine()).isNull();
        assertThat(entry.getStartLine()).isEqualTo(1);
        assertThat(entry.getPreviousLineCount()).isNull();
        assertThat(entry.getLineCount()).isEqualTo(2);

        var fullReport = reportService.getFullReport(exercise);
        assertThat(fullReport.getEntries()).hasSize(1);
        var fullEntry = fullReport.getEntries().stream().findFirst().orElseThrow();
        assertThat(fullEntry.getPreviousLine()).isNull();
        assertThat(fullEntry.getLine()).isEqualTo(1);
        assertThat(fullEntry.getPreviousCode()).isNull();
        assertThat(fullEntry.getCode()).isEqualTo("Line 1\nLine 2");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateGitDiffClearFile() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "Line 1\nLine 2", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "", exercise, solutionRepo);
        var report = reportService.updateReport(exercise);
        assertThat(report.getEntries()).hasSize(1);
        var entry = report.getEntries().stream().findFirst().orElseThrow();
        assertThat(entry.getPreviousStartLine()).isEqualTo(1);
        assertThat(entry.getStartLine()).isNull();
        assertThat(entry.getPreviousLineCount()).isEqualTo(2);
        assertThat(entry.getLineCount()).isNull();

        var fullReport = reportService.getFullReport(exercise);
        assertThat(fullReport.getEntries()).hasSize(1);
        var fullEntry = fullReport.getEntries().stream().findFirst().orElseThrow();
        assertThat(fullEntry.getPreviousLine()).isEqualTo(1);
        assertThat(fullEntry.getLine()).isNull();
        assertThat(fullEntry.getPreviousCode()).isEqualTo("Line 1\nLine 2");
        assertThat(fullEntry.getCode()).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateGitDiffDoubleModify() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "L1\nL2\nL3\nL4", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "L1\nL2a\nL3\nL4a", exercise, solutionRepo);
        var report = reportService.updateReport(exercise);
        assertThat(report.getEntries()).hasSize(2);
        var entries = new ArrayList<>(report.getEntries());
        entries.sort(Comparator.comparing(ProgrammingExerciseGitDiffEntry::getStartLine));
        assertThat(entries.get(0).getPreviousStartLine()).isEqualTo(2);
        assertThat(entries.get(0).getStartLine()).isEqualTo(2);
        assertThat(entries.get(0).getPreviousLineCount()).isEqualTo(1);
        assertThat(entries.get(0).getLineCount()).isEqualTo(1);

        assertThat(entries.get(1).getPreviousStartLine()).isEqualTo(4);
        assertThat(entries.get(1).getStartLine()).isEqualTo(4);
        assertThat(entries.get(1).getPreviousLineCount()).isEqualTo(1);
        assertThat(entries.get(1).getLineCount()).isEqualTo(1);

        var fullReport = reportService.getFullReport(exercise);
        assertThat(fullReport.getEntries()).hasSize(2);
        var fullEntries = new ArrayList<>(fullReport.getEntries());
        fullEntries.sort(Comparator.comparing(ProgrammingExerciseFullGitDiffEntryDTO::getLine));
        assertThat(fullEntries.get(0).getPreviousLine()).isEqualTo(2);
        assertThat(fullEntries.get(0).getLine()).isEqualTo(2);
        assertThat(fullEntries.get(0).getPreviousCode()).isEqualTo("L2");
        assertThat(fullEntries.get(0).getCode()).isEqualTo("L2a");

        assertThat(fullEntries.get(1).getPreviousLine()).isEqualTo(4);
        assertThat(fullEntries.get(1).getLine()).isEqualTo(4);
        assertThat(fullEntries.get(1).getPreviousCode()).isEqualTo("L4");
        assertThat(fullEntries.get(1).getCode()).isEqualTo("L4a");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateGitDiffReuseExisting() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "Line 1\nLine 2", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "Line 1\nLine 2\nLine 3\n", exercise, solutionRepo);
        var report1 = reportService.updateReport(exercise);
        assertThat(report1.getEntries()).hasSize(1);
        var report2 = reportService.updateReport(exercise);
        assertThat(report2.getEntries()).hasSize(1);
        assertThat(report1.getId()).isEqualTo(report2.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void ensureDeletionOfDuplicateReports() {
        var report1 = new ProgrammingExerciseGitDiffReport();
        report1.setProgrammingExercise(exercise);
        report1.setTemplateRepositoryCommitHash("123");
        report1.setSolutionRepositoryCommitHash("456");
        reportRepository.save(report1);
        var report2 = new ProgrammingExerciseGitDiffReport();
        report2.setProgrammingExercise(exercise);
        report2.setTemplateRepositoryCommitHash("123");
        report2.setSolutionRepositoryCommitHash("789");
        report2 = reportRepository.save(report2);

        assertThat(reportRepository.findAll()).hasSize(2);
        var returnedReport = reportService.getReportOfExercise(exercise);
        assertThat(returnedReport).isEqualTo(report2);
        assertThat(reportRepository.findAll()).hasSize(1);
    }
}
