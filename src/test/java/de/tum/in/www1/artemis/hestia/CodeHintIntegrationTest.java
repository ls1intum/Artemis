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
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.repository.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTaskRepository;

public class CodeHintIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseHintRepository exerciseHintRepository;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    public void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(2, 2, 1, 2);

        exercise = exerciseRepository.findAll().get(0);

        var task = new ProgrammingExerciseTask().taskName("Test Task").exercise(exercise);
        task = programmingExerciseTaskRepository.save(task);
        var codeHint = new CodeHint().programmingExerciseTask(task).exercise(exercise).title("Test Code Hint");
        exerciseHintRepository.save(codeHint);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void queryAllHintsForAnExerciseAsAStudent() throws Exception {
        request.getList("/api/exercises/" + exercise.getId() + "/code-hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void queryAllHintsForAnExerciseAsATutor() throws Exception {
        request.getList("/api/exercises/" + exercise.getId() + "/code-hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void queryAllHintsForAnExerciseAsAnInstructor() throws Exception {
        request.getList("/api/exercises/" + exercise.getId() + "/code-hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getHintForAnExerciseAsStudentShouldReturnForbidden() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/exercise-hints/" + exerciseHint.getId(), HttpStatus.FORBIDDEN, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getHintForAnExerciseAsTutorForbidden() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/code-hints/" + exerciseHint.getId(), HttpStatus.FORBIDDEN, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void getHintForAnExerciseAsEditor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/code-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getHintForAnExerciseAsAnInstructor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/code-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
        request.get("/api/code-hints/" + 0L, HttpStatus.NOT_FOUND, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetHintTitleAsInstructor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetHintTitle();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetHintTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetHintTitle();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetHintTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetHintTitle();
    }

    private void testGetHintTitle() throws Exception {
        final var hint = new CodeHint().title("Test Hint").exercise(exercise);
        exerciseHintRepository.save(hint);

        final var title = request.get("/api/code-hints/" + hint.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(hint.getTitle());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetHintTitleForNonExistingHint() throws Exception {
        request.get("/api/code-hints/12312312321/title", HttpStatus.NOT_FOUND, String.class);
    }
}
