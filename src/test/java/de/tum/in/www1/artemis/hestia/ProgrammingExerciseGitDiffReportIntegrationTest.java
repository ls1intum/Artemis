package de.tum.in.www1.artemis.hestia;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.in.www1.artemis.util.HestiaUtilTestService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.hestia.ProgrammingExerciseFullGitDiffReportDTO;

/**
 * Tests for the ProgrammingExerciseGitDiffReportResource
 */
class ProgrammingExerciseGitDiffReportIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final static String FILE_NAME = "test.java";

    private final LocalRepository solutionRepo = new LocalRepository("main");

    private final LocalRepository templateRepo = new LocalRepository("main");

    private ProgrammingExercise exercise;

    @Autowired
    private HestiaUtilTestService hestiaUtilTestService;

    @Autowired
    private ProgrammingExerciseGitDiffReportService reportService;

    @BeforeEach
    void initTestCase() throws Exception {
        Course course = database.addEmptyCourse();
        database.addUsers(1, 1, 1, 1);
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getGitDiffAsAStudent() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/diff-report", HttpStatus.FORBIDDEN, ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getGitDiffAsATutor() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/diff-report", HttpStatus.FORBIDDEN, ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void getGitDiffAsAnEditor() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/diff-report", HttpStatus.OK, ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getGitDiffAsAnInstructor() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/diff-report", HttpStatus.OK, ProgrammingExerciseGitDiffReport.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getFullGitDiffAsAStudent() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/full-diff-report", HttpStatus.FORBIDDEN, ProgrammingExerciseFullGitDiffReportDTO.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getFullGitDiffAsATutor() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/full-diff-report", HttpStatus.FORBIDDEN, ProgrammingExerciseFullGitDiffReportDTO.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void getFullGitDiffAsAnEditor() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/full-diff-report", HttpStatus.OK, ProgrammingExerciseFullGitDiffReportDTO.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getFullGitDiffAsAnInstructor() throws Exception {
        exercise = hestiaUtilTestService.setupTemplate(FILE_NAME, "TEST", exercise, templateRepo);
        exercise = hestiaUtilTestService.setupSolution(FILE_NAME, "TEST", exercise, solutionRepo);
        reportService.updateReport(exercise);
        request.get("/api/programming-exercises/" + exercise.getId() + "/full-diff-report", HttpStatus.OK, ProgrammingExerciseFullGitDiffReportDTO.class);
    }
}
