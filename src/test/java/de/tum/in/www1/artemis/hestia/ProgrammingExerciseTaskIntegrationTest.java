package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;

public class ProgrammingExerciseTaskIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    public void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(2, 2, 1, 2);

        exercise = exerciseRepository.findAll().get(0);
        var task = new ProgrammingExerciseTask().taskName("Test Task").exercise(exercise);
        programmingExerciseTaskRepository.save(task);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void queryAllTasksForAnExerciseAsAStudent() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/programming-exercise-tasks", HttpStatus.OK, ProgrammingExerciseTask.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void queryAllTasksForAnExerciseAsATutor() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/programming-exercise-tasks", HttpStatus.OK, ProgrammingExerciseTask.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void queryAllTasksForAnExerciseAsAnInstructor() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/programming-exercise-tasks", HttpStatus.OK, ProgrammingExerciseTask.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getTaskForAnExerciseAsStudentShouldReturnForbidden() throws Exception {
        ProgrammingExerciseTask task = programmingExerciseTaskRepository.findAll().get(0);
        request.get("/api/programming-exercise-tasks/" + task.getId(), HttpStatus.FORBIDDEN, ProgrammingExerciseTask.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getTaskForAnExerciseAsTutorForbidden() throws Exception {
        ProgrammingExerciseTask task = programmingExerciseTaskRepository.findAll().get(0);
        request.get("/api/programming-exercise-tasks/" + task.getId(), HttpStatus.FORBIDDEN, ProgrammingExerciseTask.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void getTaskForAnExerciseAsEditor() throws Exception {
        ProgrammingExerciseTask task = programmingExerciseTaskRepository.findAll().get(0);
        request.get("/api/programming-exercise-tasks/" + task.getId(), HttpStatus.OK, ProgrammingExerciseTask.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getTaskForAnExerciseAsAnInstructor() throws Exception {
        ProgrammingExerciseTask task = programmingExerciseTaskRepository.findAll().get(0);
        request.get("/api/programming-exercise-tasks/" + task.getId(), HttpStatus.OK, ProgrammingExerciseTask.class);
        request.get("/api/programming-exercise-tasks/" + 0L, HttpStatus.NOT_FOUND, ProgrammingExerciseTask.class);
    }

    @Test
    @WithMockUser(username = "user1", roles = "EDITOR")
    public void testGetNonExistingTask() throws Exception {
        request.get("/api/programming-exercise-tasks/12312312321", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetTaskNameAsInstructor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetTaskName();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetTaskNameAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetTaskName();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetTaskNameAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetTaskName();
    }

    private void testGetTaskName() throws Exception {
        var task = new ProgrammingExerciseTask().taskName("Test Task").exercise(exercise);
        programmingExerciseTaskRepository.save(task);

        var name = request.get("/api/programming-exercise-tasks/" + task.getId() + "/name", HttpStatus.OK, String.class);
        assertThat(name).isEqualTo(task.getTaskName());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetTaskNameForNonExistingTask() throws Exception {
        request.get("/api/programming-exercise-tasks/12312312321/name", HttpStatus.NOT_FOUND, String.class);
    }
}
