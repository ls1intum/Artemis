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
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.TextHint;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;

public class TextHintIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
        request.getList("/api/exercises/" + exercise.getId() + "/text-hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void queryAllHintsForAnExerciseAsAnInstructor() throws Exception {
        request.getList("/api/exercises/" + exercise.getId() + "/text-hints", HttpStatus.OK, ExerciseHint.class);
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
        request.get("/api/exercise-hints/" + exerciseHint.getId(), HttpStatus.FORBIDDEN, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void getHintForAnExerciseAsEditor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getHintForAnExerciseAsAnInstructor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
        request.get("/api/exercise-hints/" + 0L, HttpStatus.NOT_FOUND, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void createHintAsAStudentShouldReturnForbidden() throws Exception {
        ExerciseHint exerciseHint = new TextHint().content("content 4").title("title 4").exercise(exercise);
        request.post("/api/text-hints/", exerciseHint, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void createHintAsTutorForbidden() throws Exception {
        ExerciseHint exerciseHint = new TextHint().content("content 4").title("title 4").exercise(exercise);
        request.post("/api/text-hints/", exerciseHint, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void createHintAsEditor() throws Exception {
        ExerciseHint exerciseHint = new TextHint().content("content 4").title("title 4").exercise(exercise);
        request.post("/api/text-hints/", exerciseHint, HttpStatus.CREATED);

        List<ExerciseHint> exerciseHints = exerciseHintRepository.findAll();
        assertThat(exerciseHints).hasSize(4);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = new TextHint().content("content 4").title("title 4").exercise(exercise);
        request.post("/api/text-hints/", exerciseHint, HttpStatus.CREATED);
        List<ExerciseHint> exerciseHints = exerciseHintRepository.findAll();
        assertThat(exerciseHints).hasSize(4);

        exerciseHint.setExercise(null);
        request.post("/api/text-hints/", exerciseHint, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updateHintAsAStudentShouldReturnForbidden() throws Exception {
        updateHintForbidden();
    }

    private void updateHintForbidden() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        assertThat(exerciseHint).isInstanceOf(TextHint.class);
        if (exerciseHint instanceof TextHint textHint) {
            String newContent = "new content value!";
            String contentBefore = textHint.getContent();
            textHint.setContent(newContent);
            request.put("/api/text-hints/" + textHint.getId(), textHint, HttpStatus.FORBIDDEN);

            Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(textHint.getId());
            assertThat(hintAfterSave).isPresent();
            assertThat(hintAfterSave.get()).isInstanceOf(TextHint.class);
            assertThat(((TextHint) hintAfterSave.get()).getContent()).isEqualTo(contentBefore);
        }
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

        assertThat(exerciseHint).isInstanceOf(TextHint.class);
        if (exerciseHint instanceof TextHint textHint) {
            textHint.setContent(newContent);
            request.put("/api/text-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.OK);
            request.put("/api/text-hints/" + 0L, exerciseHint, HttpStatus.BAD_REQUEST);
            Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
            assertThat(hintAfterSave).isPresent();
            assertThat(hintAfterSave.get()).isInstanceOf(TextHint.class);
            assertThat(((TextHint) hintAfterSave.get()).getContent()).isEqualTo(newContent);
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        String newContent = "new content value!";

        assertThat(exerciseHint).isInstanceOf(TextHint.class);
        if (exerciseHint instanceof TextHint textHint) {
            textHint.setContent(newContent);
            request.put("/api/text-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.OK);

            Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
            assertThat(hintAfterSave).isPresent();
            assertThat(hintAfterSave.get()).isInstanceOf(TextHint.class);
            assertThat(((TextHint) hintAfterSave.get()).getContent()).isEqualTo(newContent);
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = new TextHint().content("content 4").title("title 4").exercise(exercise);
        request.delete("/api/text-hints/" + 0L, HttpStatus.NOT_FOUND);
        request.post("/api/text-hints", exerciseHint, HttpStatus.CREATED);
        List<ExerciseHint> exerciseHints = exerciseHintRepository.findAll();
        request.delete("/api/text-hints/" + exerciseHints.get(0).getId(), HttpStatus.NO_CONTENT);
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
        final var hint = new TextHint().title("Test Hint").exercise(exercise);
        exerciseHintRepository.save(hint);

        final var title = request.get("/api/exercise-hints/" + hint.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(hint.getTitle());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetHintTitleForNonExistingHint() throws Exception {
        request.get("/api/exercise-hints/12312312321/title", HttpStatus.NOT_FOUND, String.class);
    }
}
