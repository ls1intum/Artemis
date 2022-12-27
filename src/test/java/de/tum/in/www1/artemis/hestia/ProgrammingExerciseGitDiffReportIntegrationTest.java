package de.tum.in.www1.artemis.hestia;

import java.time.ZonedDateTime;

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

/**
 * Tests for the ProgrammingExerciseGitDiffReportResource
 */
class ProgrammingExerciseGitDiffReportIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "progexgitdiffreport";

    private static final String FILE_NAME = "test.java";

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
        database.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
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
}
