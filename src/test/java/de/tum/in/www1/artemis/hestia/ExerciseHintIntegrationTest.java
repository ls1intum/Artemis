package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;

public class ExerciseHintIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseHintRepository exerciseHintRepository;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    private Exercise exercise;

    @BeforeEach
    public void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(2, 2, 1, 2);

        exercise = exerciseRepository.findAll().get(0);
        database.addHintsToExercise(exercise);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void queryAllHintsForAnExerciseAsAStudent() throws Exception {
        request.getList("/api/exercises/" + exercise.getId() + "/exercise-hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void queryAllHintsForAnExerciseAsATutor() throws Exception {
        request.getList("/api/exercises/" + exercise.getId() + "/exercise-hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void queryAllHintsForAnExerciseAsAnInstructor() throws Exception {
        request.getList("/api/exercises/" + exercise.getId() + "/exercise-hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getHintForAnExerciseAsStudentShouldReturnForbidden() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.FORBIDDEN, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getHintForAnExerciseAsTutorForbidden() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.FORBIDDEN, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void getHintForAnExerciseAsEditor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getHintForAnExerciseAsAnInstructor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
        request.get("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + 0L, HttpStatus.NOT_FOUND, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void createHintAsAStudentShouldReturnForbidden() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exercise);
        request.post("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/", exerciseHint, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void createHintAsTutorForbidden() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exercise);
        request.post("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/", exerciseHint, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void createHintAsEditor() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exercise);
        request.post("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/", exerciseHint, HttpStatus.CREATED);

        List<ExerciseHint> exerciseHints = exerciseHintRepository.findAll();
        assertThat(exerciseHints).hasSize(4);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exercise);

        request.post("/api/exercises/" + exercise.getId() + "/exercise-hints/", exerciseHint, HttpStatus.CREATED);
        List<ExerciseHint> exerciseHints = exerciseHintRepository.findAll();
        assertThat(exerciseHints).hasSize(4);

        exerciseHint.setExercise(null);
        request.post("/api/exercises/" + exercise.getId() + "/exercise-hints/", exerciseHint, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createCodeHintShouldFail() throws Exception {
        CodeHint codeHint = new CodeHint();
        codeHint.setTitle("Hint 1");
        codeHint.setExercise(exercise);

        int sizeBefore = exerciseHintRepository.findAll().size();
        request.post("/api/exercises/" + codeHint.getExercise().getId() + "/exercise-hints/", codeHint, HttpStatus.BAD_REQUEST);
        int sizeAfter = exerciseHintRepository.findAll().size();
        assertThat(sizeAfter).isEqualTo(sizeBefore);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updateHintAsAStudentShouldReturnForbidden() throws Exception {
        updateHintForbidden();
    }

    private void updateHintForbidden() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        String newContent = "new content value!";
        String contentBefore = exerciseHint.getContent();
        exerciseHint.setContent(newContent);
        request.put("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.FORBIDDEN);

        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
        assertThat(hintAfterSave).isPresent();
        assertThat(hintAfterSave.get()).isInstanceOf(ExerciseHint.class);
        assertThat((hintAfterSave.get()).getContent()).isEqualTo(contentBefore);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void updateHintAsTutorForbidden() throws Exception {
        updateHintForbidden();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void updateHintAsEditor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        String newContent = "new content value!";
        exerciseHint.setContent(newContent);
        request.put("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.OK);
        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
        assertThat(hintAfterSave).isPresent();
        assertThat(hintAfterSave.get()).isInstanceOf(ExerciseHint.class);
        assertThat((hintAfterSave.get()).getContent()).isEqualTo(newContent);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        String newContent = "new content value!";

        exerciseHint.setContent(newContent);
        request.put("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.OK);

        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
        assertThat(hintAfterSave).isPresent();
        assertThat(hintAfterSave.get()).isInstanceOf(ExerciseHint.class);
        assertThat((hintAfterSave.get()).getContent()).isEqualTo(newContent);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateCodeHintShouldFail() throws Exception {
        CodeHint codeHint = new CodeHint();
        codeHint.setTitle("Hint 1");
        codeHint.setExercise(exercise);
        exerciseHintRepository.save(codeHint);

        codeHint.setTitle("New Title");

        request.put("/api/exercises/" + codeHint.getExercise().getId() + "/exercise-hints/" + codeHint.getId(), codeHint, HttpStatus.BAD_REQUEST);
        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(codeHint.getId());
        assertThat(hintAfterSave).isPresent();
        assertThat(hintAfterSave.get()).isInstanceOf(CodeHint.class);
        assertThat((hintAfterSave.get()).getTitle()).isEqualTo("Hint 1");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exercise);
        request.delete("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + 0L, HttpStatus.NOT_FOUND);
        request.post("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints", exerciseHint, HttpStatus.CREATED);
        List<ExerciseHint> exerciseHints = exerciseHintRepository.findAll();
        request.delete("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHints.get(0).getId(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteCodeHintAsInstructor() throws Exception {
        CodeHint codeHint = new CodeHint();
        codeHint.setTitle("Hint 1");
        codeHint.setExercise(exercise);
        exerciseHintRepository.save(codeHint);

        request.delete("/api/exercises/" + codeHint.getExercise().getId() + "/exercise-hints/" + codeHint.getId(), HttpStatus.NO_CONTENT);
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
        final var hint = new ExerciseHint().title("Test Hint").exercise(exercise);
        exerciseHintRepository.save(hint);

        final var title = request.get("/api/exercises/" + hint.getExercise().getId() + "/exercise-hints/" + hint.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(hint.getTitle());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetHintTitleForNonExistingHint() throws Exception {
        request.get("/api/exercises/" + exercise.getId() + "/exercise-hints/12312312321/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createHintWithInvalidExerciseIds() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        ExerciseHint exerciseHint = new ExerciseHint();
        exerciseHint.setTitle("Test Title");
        exerciseHint.setExercise(exercise);

        request.post("/api/exercises/" + unrelatedExercise.getId() + "/exercise-hints", exerciseHint, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateHintWithInvalidExerciseIds() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        exerciseHint.setTitle("New Title");

        request.put("/api/exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getHintTitleWithInvalidExerciseIds() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);

        request.get("/api/exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.CONFLICT, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getExerciseHintWithInvalidExerciseIds() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);

        request.get("/api/exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.CONFLICT, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteHintWithInvalidExerciseIds() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);

        request.delete("/api/exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.CONFLICT);
    }
}
