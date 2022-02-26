package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.in.www1.artemis.util.HestiaUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.hestia.ProgrammingExerciseFullGitDiffEntryDTO;

/**
 * Tests for the ProgrammingExerciseGitDiffReportService
 */
public class ProgrammingExerciseGitDiffReportServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final static String FILE_NAME = "test.java";

    private final LocalRepository solutionRepo = new LocalRepository();

    private final LocalRepository templateRepo = new LocalRepository();

    private ProgrammingExercise exercise;

    @Autowired
    private HestiaUtilService hestiaUtilService;

    @Autowired
    private ProgrammingExerciseGitDiffReportService reportService;

    @BeforeEach
    public void initTestCase() throws Exception {
        Course course = database.addEmptyCourse();
        database.addUsers(1, 1, 1, 1);
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        gitService.deleteLocalRepository(exercise.getVcsTemplateRepositoryUrl());
        gitService.deleteLocalRepository(exercise.getVcsSolutionRepositoryUrl());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getNonExistingGitDiff() throws Exception {
        exercise = hestiaUtilService.setupTemplate("Line 1\nLine 2", FILE_NAME, exercise, templateRepo);
        exercise = hestiaUtilService.setupSolution("Line 1\nLine 2", FILE_NAME, exercise, solutionRepo);
        var report = reportService.getFullReport(exercise);
        assertThat(report).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffNoChanges() throws Exception {
        exercise = hestiaUtilService.setupTemplate("Line 1\nLine 2", FILE_NAME, exercise, templateRepo);
        exercise = hestiaUtilService.setupSolution("Line 1\nLine 2", FILE_NAME, exercise, solutionRepo);
        var report = reportService.updateReport(exercise);
        assertThat(report.getEntries()).isNullOrEmpty();

        var fullReport = reportService.getFullReport(exercise);
        assertThat(fullReport.getEntries()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffAppendLine1() throws Exception {
        exercise = hestiaUtilService.setupTemplate("Line 1\nLine 2", FILE_NAME, exercise, templateRepo);
        exercise = hestiaUtilService.setupSolution("Line 1\nLine 2\nLine 3\n", FILE_NAME, exercise, solutionRepo);
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
    public void updateGitDiffAppendLine2() throws Exception {
        exercise = hestiaUtilService.setupTemplate("Line 1\nLine 2\n", FILE_NAME, exercise, templateRepo);
        exercise = hestiaUtilService.setupSolution("Line 1\nLine 2\nLine 3\n", FILE_NAME, exercise, solutionRepo);
        var report = reportService.updateReport(exercise);
        assertThat(report.getEntries()).hasSize(1);
        var entry = report.getEntries().stream().findFirst().orElseThrow();
        assertThat(entry.getPreviousStartLine()).isEqualTo(null);
        assertThat(entry.getStartLine()).isEqualTo(3);
        assertThat(entry.getPreviousLineCount()).isEqualTo(null);
        assertThat(entry.getLineCount()).isEqualTo(1);

        var fullReport = reportService.getFullReport(exercise);
        assertThat(fullReport.getEntries()).hasSize(1);
        var fullEntry = fullReport.getEntries().stream().findFirst().orElseThrow();
        assertThat(fullEntry.getPreviousLine()).isEqualTo(null);
        assertThat(fullEntry.getLine()).isEqualTo(3);
        assertThat(fullEntry.getPreviousCode()).isEqualTo(null);
        assertThat(fullEntry.getCode()).isEqualTo("Line 3");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffAddToEmptyFile() throws Exception {
        exercise = hestiaUtilService.setupTemplate("", FILE_NAME, exercise, templateRepo);
        exercise = hestiaUtilService.setupSolution("Line 1\nLine 2", FILE_NAME, exercise, solutionRepo);
        var report = reportService.updateReport(exercise);
        assertThat(report.getEntries()).hasSize(1);
        var entry = report.getEntries().stream().findFirst().orElseThrow();
        assertThat(entry.getPreviousStartLine()).isEqualTo(null);
        assertThat(entry.getStartLine()).isEqualTo(1);
        assertThat(entry.getPreviousLineCount()).isEqualTo(null);
        assertThat(entry.getLineCount()).isEqualTo(2);

        var fullReport = reportService.getFullReport(exercise);
        assertThat(fullReport.getEntries()).hasSize(1);
        var fullEntry = fullReport.getEntries().stream().findFirst().orElseThrow();
        assertThat(fullEntry.getPreviousLine()).isEqualTo(null);
        assertThat(fullEntry.getLine()).isEqualTo(1);
        assertThat(fullEntry.getPreviousCode()).isEqualTo(null);
        assertThat(fullEntry.getCode()).isEqualTo("Line 1\nLine 2");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffClearFile() throws Exception {
        exercise = hestiaUtilService.setupTemplate("Line 1\nLine 2", FILE_NAME, exercise, templateRepo);
        exercise = hestiaUtilService.setupSolution("", FILE_NAME, exercise, solutionRepo);
        var report = reportService.updateReport(exercise);
        assertThat(report.getEntries()).hasSize(1);
        var entry = report.getEntries().stream().findFirst().orElseThrow();
        assertThat(entry.getPreviousStartLine()).isEqualTo(1);
        assertThat(entry.getStartLine()).isEqualTo(null);
        assertThat(entry.getPreviousLineCount()).isEqualTo(2);
        assertThat(entry.getLineCount()).isEqualTo(null);

        var fullReport = reportService.getFullReport(exercise);
        assertThat(fullReport.getEntries()).hasSize(1);
        var fullEntry = fullReport.getEntries().stream().findFirst().orElseThrow();
        assertThat(fullEntry.getPreviousLine()).isEqualTo(1);
        assertThat(fullEntry.getLine()).isEqualTo(null);
        assertThat(fullEntry.getPreviousCode()).isEqualTo("Line 1\nLine 2");
        assertThat(fullEntry.getCode()).isEqualTo(null);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffDoubleModify() throws Exception {
        exercise = hestiaUtilService.setupTemplate("L1\nL2\nL3\nL4", FILE_NAME, exercise, templateRepo);
        exercise = hestiaUtilService.setupSolution("L1\nL2a\nL3\nL4a", FILE_NAME, exercise, solutionRepo);
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
        assertThat(fullEntries.get(0).getPreviousCode()).isEqualTo("L2\n");
        assertThat(fullEntries.get(0).getCode()).isEqualTo("L2a\n");

        assertThat(fullEntries.get(1).getPreviousLine()).isEqualTo(4);
        assertThat(fullEntries.get(1).getLine()).isEqualTo(4);
        assertThat(fullEntries.get(1).getPreviousCode()).isEqualTo("L4");
        assertThat(fullEntries.get(1).getCode()).isEqualTo("L4a");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffReuseExisting() throws Exception {
        exercise = hestiaUtilService.setupTemplate("Line 1\nLine 2", FILE_NAME, exercise, templateRepo);
        exercise = hestiaUtilService.setupSolution("Line 1\nLine 2\nLine 3\n", FILE_NAME, exercise, solutionRepo);
        var report1 = reportService.updateReport(exercise);
        assertThat(report1.getEntries()).hasSize(1);
        var report2 = reportService.updateReport(exercise);
        assertThat(report2.getEntries()).hasSize(1);
        assertThat(report1.getId()).isEqualTo(report2.getId());
    }
}
