package de.tum.in.www1.artemis.hestia;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.localvcci.AbstractLocalCILocalVCIntegrationTest;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.HestiaUtilTestService;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * Tests for the ProgrammingExerciseGitDiffReportResource
 */
class ProgrammingExerciseGitDiffReportIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

    private static final String TEST_PREFIX = "progexgitdiffreport";

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private static final String FILE_NAME = "test.java";

    private static final String FILE_NAME2 = "test2.java";

    private final LocalRepository solutionRepo = new LocalRepository("main");

    private final LocalRepository templateRepo = new LocalRepository("main");

    private final LocalRepository participationRepo = new LocalRepository("main");

    private ProgrammingExercise exercise;

    @Autowired
    private HestiaUtilTestService hestiaUtilTestService;

    @Autowired
    private ProgrammingExerciseGitDiffReportService reportService;

    @BeforeEach
    void initTestCase() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
    }

    @AfterEach
    void cleanup() throws Exception {
        solutionRepo.resetLocalRepo();
        templateRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getGitDiffAsAStudent() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/diff-report", HttpStatus.FORBIDDEN, ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getGitDiffAsATutor() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/diff-report", HttpStatus.OK, ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getGitDiffAsAnEditor() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/diff-report", HttpStatus.OK, ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getGitDiffAsAnInstructor() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/diff-report", HttpStatus.OK, ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getGitDiffBetweenTemplateAndSubmission() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "ABC", exercise, templateRepo);
        participationRepo.configureRepos("participationLocalRepo", "participationOriginRepo");
        var studentLogin = TEST_PREFIX + "student1";
        var submission = hestiaUtilTestService.setupSubmission(FILE_NAME, "TEST", exercise, participationRepo, studentLogin);
        request.get("/api/programming-exercises/" + exercise.getId() + "/submissions/" + submission.getId() + "/diff-report-with-template", HttpStatus.OK,
                ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getGitDiffBetweenTemplateAndSubmissionEditorForbidden() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "ABC", exercise, templateRepo);
        participationRepo.configureRepos("participationLocalRepo", "participationOriginRepo");
        var studentLogin = TEST_PREFIX + "student1";
        var submission = hestiaUtilTestService.setupSubmission(FILE_NAME, "TEST", exercise, participationRepo, studentLogin);
        request.get("/api/programming-exercises/" + exercise.getId() + "/submissions/" + submission.getId() + "/diff-report-with-template", HttpStatus.FORBIDDEN,
                ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getGitDiffReportForCommits() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "ABC", exercise, templateRepo);
        participationRepo.configureRepos("participationLocalRepo", "participationOriginRepo");
        var studentLogin = TEST_PREFIX + "student1";
        var submission = hestiaUtilTestService.setupSubmission(FILE_NAME, "TEST", exercise, participationRepo, studentLogin);
        var submission2 = hestiaUtilTestService.setupSubmission(FILE_NAME, "TEST2", exercise, participationRepo, studentLogin);
        request.get("/api/programming-exercises/" + exercise.getId() + "/participation/" + submission.getParticipation().getId() + "/commits/" + submission.getCommitHash()
                + "/diff-report/" + submission2.getCommitHash(), HttpStatus.OK, ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getGitDiffReportForCommitsThrowsConflictException() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "ABC", exercise, templateRepo);
        var wrongExerciseId = exercise.getId() + 1;
        participationRepo.configureRepos("participationLocalRepo", "participationOriginRepo");
        var studentLogin = TEST_PREFIX + "student1";
        var submission = hestiaUtilTestService.setupSubmission(FILE_NAME, "TEST", exercise, participationRepo, studentLogin);
        var submission2 = hestiaUtilTestService.setupSubmission(FILE_NAME, "TEST2", exercise, participationRepo, studentLogin);
        request.get("/api/programming-exercises/" + wrongExerciseId + "/participation/" + submission.getParticipation().getId() + "/commits/" + submission.getCommitHash()
                + "/diff-report/" + submission2.getCommitHash(), HttpStatus.CONFLICT, ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getGitDiffReportForCommitsForbiddenIfNotParticipationOwner() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "ABC", exercise, templateRepo);
        participationRepo.configureRepos("participationLocalRepo", "participationOriginRepo");
        // Create a submission for student2 and try to access it with student1
        var studentLogin = TEST_PREFIX + "instructor1";
        var submission = hestiaUtilTestService.setupSubmission(FILE_NAME, "TEST", exercise, participationRepo, studentLogin);
        var submission2 = hestiaUtilTestService.setupSubmission(FILE_NAME, "TEST2", exercise, participationRepo, studentLogin);
        request.get("/api/programming-exercises/" + exercise.getId() + "/participation/" + submission.getParticipation().getId() + "/commits/" + submission.getCommitHash()
                + "/diff-report/" + submission2.getCommitHash(), HttpStatus.FORBIDDEN, ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getGitDiffBetweenTwoSubmissions() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "ABC", exercise, templateRepo);
        participationRepo.configureRepos("participationLocalRepo", "participationOriginRepo");
        var studentLogin = TEST_PREFIX + "student1";
        var submission = hestiaUtilTestService.setupSubmission(FILE_NAME, "TEST", exercise, participationRepo, studentLogin);
        var submission2 = hestiaUtilTestService.setupSubmission(FILE_NAME, "TEST2", exercise, participationRepo, studentLogin);
        request.get("/api/programming-exercises/" + exercise.getId() + "/submissions/" + submission.getId() + "/diff-report/" + submission2.getId(), HttpStatus.OK,
                ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getGitDiffBetweenTwoSubmissionsEditorForbidden() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "ABC", exercise, templateRepo);
        participationRepo.configureRepos("participationLocalRepo", "participationOriginRepo");
        var studentLogin = TEST_PREFIX + "student1";
        var submission = hestiaUtilTestService.setupSubmission(FILE_NAME, "TEST", exercise, participationRepo, studentLogin);
        var submission2 = hestiaUtilTestService.setupSubmission(FILE_NAME2, "TEST2", exercise, participationRepo, studentLogin);
        request.get("/api/programming-exercises/" + exercise.getId() + "/submissions/" + submission.getId() + "/diff-report/" + submission2.getId(), HttpStatus.FORBIDDEN,
                ProgrammingExerciseGitDiffReport.class);
    }
}
