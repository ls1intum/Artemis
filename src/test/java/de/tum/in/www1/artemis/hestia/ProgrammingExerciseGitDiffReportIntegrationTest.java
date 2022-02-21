package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

/**
 * Tests for the ProgrammingExerciseGitDiffReportResource and -Service
 */
public class ProgrammingExerciseGitDiffReportIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final static String fileName = "test.java";

    private final LocalRepository templateRepo = new LocalRepository();

    private final LocalRepository solutionRepo = new LocalRepository();

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    public void initTestCase() throws Exception {
        Course course = database.addEmptyCourse();
        database.addUsers(1, 1, 1, 1);
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
    }

    private void setupTemplate(String content) throws Exception {
        templateRepo.configureRepos("templateLocalRepo", "templateOriginRepo");

        // add file to the repository folder
        Path filePath = Paths.get(templateRepo.localRepoFile + "/" + fileName);
        File templateFile = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(templateFile, content, Charset.defaultCharset());

        var templateRepoUrl = new GitUtilService.MockFileRepositoryUrl(templateRepo.localRepoFile);
        exercise.setTemplateRepositoryUrl(templateRepoUrl.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(templateRepoUrl, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(templateRepoUrl, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(templateRepoUrl),
                eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(templateRepoUrl),
                eq(false), any());

        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.mockDefaultBranch("master", urlService.getProjectKeyFromRepositoryUrl(templateRepoUrl));

        exercise = exerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        var templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElseThrow();
        templateParticipation.setRepositoryUrl(templateRepoUrl.toString());
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        var templateSubmission = new ProgrammingSubmission();
        templateSubmission.setParticipation(templateParticipation);
        templateSubmission.setCommitHash("x");
        programmingSubmissionRepository.save(templateSubmission);
    }

    private void setupSolution(String content) throws Exception {
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");

        // add file to the repository folder
        Path filePath = Paths.get(solutionRepo.localRepoFile + "/" + fileName);
        File solutionFile = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(solutionFile, content, Charset.defaultCharset());

        var solutionRepoUrl = new GitUtilService.MockFileRepositoryUrl(solutionRepo.localRepoFile);
        exercise.setSolutionRepositoryUrl(solutionRepoUrl.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(solutionRepoUrl, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(solutionRepoUrl, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(solutionRepoUrl),
                eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(solutionRepoUrl),
                eq(false), any());

        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.mockDefaultBranch("master", urlService.getProjectKeyFromRepositoryUrl(solutionRepoUrl));

        exercise = exerciseRepository.save(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);
        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElseThrow();
        solutionParticipation.setRepositoryUrl(solutionRepoUrl.toString());
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        var solutionSubmission = new ProgrammingSubmission();
        solutionSubmission.setParticipation(solutionParticipation);
        solutionSubmission.setCommitHash("x");
        programmingSubmissionRepository.save(solutionSubmission);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updateGitDiffAsAStudent() throws Exception {
        setupTemplate("Test");
        setupSolution("Test");
        request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void updateGitDiffAsATutor() throws Exception {
        setupTemplate("Test");
        setupSolution("Test");
        request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void updateGitDiffAsAnEditor() throws Exception {
        setupTemplate("Test");
        setupSolution("Test");
        request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffAsAnInstructor() throws Exception {
        setupTemplate("Test");
        setupSolution("Test");
        request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffNoChanges() throws Exception {
        setupTemplate("Line 1\nLine 2");
        setupSolution("Line 1\nLine 2");
        var report = request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.OK);
        assertThat(report.getEntries()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffAppendLine1() throws Exception {
        setupTemplate("Line 1\nLine 2");
        setupSolution("Line 1\nLine 2\nLine 3\n");
        var report = request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.OK);
        assertThat(report.getEntries()).hasSize(1);
        var entry = report.getEntries().stream().findFirst().orElseThrow();
        assertThat(entry.getPreviousLine()).isEqualTo(2);
        assertThat(entry.getLine()).isEqualTo(2);
        assertThat(entry.getPreviousCode()).isEqualTo("Line 2");
        assertThat(entry.getCode()).isEqualTo("Line 2\nLine 3");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffAppendLine2() throws Exception {
        setupTemplate("Line 1\nLine 2\n");
        setupSolution("Line 1\nLine 2\nLine 3\n");
        var report = request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.OK);
        assertThat(report.getEntries()).hasSize(1);
        var entry = report.getEntries().stream().findFirst().orElseThrow();
        assertThat(entry.getPreviousLine()).isEqualTo(null);
        assertThat(entry.getLine()).isEqualTo(3);
        assertThat(entry.getPreviousCode()).isEqualTo(null);
        assertThat(entry.getCode()).isEqualTo("Line 3");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffAddToEmptyFile() throws Exception {
        setupTemplate("");
        setupSolution("Line 1\nLine 2");
        var report = request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.OK);
        assertThat(report.getEntries()).hasSize(1);
        var entry = report.getEntries().stream().findFirst().orElseThrow();
        assertThat(entry.getPreviousLine()).isEqualTo(null);
        assertThat(entry.getLine()).isEqualTo(1);
        assertThat(entry.getPreviousCode()).isEqualTo(null);
        assertThat(entry.getCode()).isEqualTo("Line 1\nLine 2");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffClearFile() throws Exception {
        setupTemplate("Line 1\nLine 2");
        setupSolution("");
        var report = request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.OK);
        assertThat(report.getEntries()).hasSize(1);
        var entry = report.getEntries().stream().findFirst().orElseThrow();
        assertThat(entry.getPreviousLine()).isEqualTo(1);
        assertThat(entry.getLine()).isEqualTo(null);
        assertThat(entry.getPreviousCode()).isEqualTo("Line 1\nLine 2");
        assertThat(entry.getCode()).isEqualTo(null);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffDoubleModify() throws Exception {
        setupTemplate("L1\nL2\nL3\nL4");
        setupSolution("L1\nL2a\nL3\nL4a");
        var report = request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.OK);
        assertThat(report.getEntries()).hasSize(2);
        var entries = new ArrayList<>(report.getEntries());
        entries.sort(Comparator.comparing(ProgrammingExerciseGitDiffEntry::getLine));
        assertThat(entries.get(0).getPreviousLine()).isEqualTo(2);
        assertThat(entries.get(0).getLine()).isEqualTo(2);
        assertThat(entries.get(0).getPreviousCode()).isEqualTo("L2");
        assertThat(entries.get(0).getCode()).isEqualTo("L2a");

        assertThat(entries.get(1).getPreviousLine()).isEqualTo(4);
        assertThat(entries.get(1).getLine()).isEqualTo(4);
        assertThat(entries.get(1).getPreviousCode()).isEqualTo("L4");
        assertThat(entries.get(1).getCode()).isEqualTo("L4a");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateGitDiffReuseExisting() throws Exception {
        setupTemplate("Line 1\nLine 2");
        setupSolution("Line 1\nLine 2\nLine 3\n");
        var report1 = request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.OK);
        assertThat(report1.getEntries()).hasSize(1);

        var report2 = request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/diff-report", null, ProgrammingExerciseGitDiffReport.class, HttpStatus.OK);
        assertThat(report2.getEntries()).hasSize(1);

        assertThat(report1.getId()).isEqualTo(report2.getId());
    }
}
