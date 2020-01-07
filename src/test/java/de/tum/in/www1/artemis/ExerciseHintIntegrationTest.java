package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ExerciseHint;
import de.tum.in.www1.artemis.repository.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.ExerciseHintResource;

public class ExerciseHintIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    ExerciseHintResource exerciseHintResource;

    @Autowired
    ExerciseHintRepository exerciseHintRepository;

    @Autowired
    ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    Exercise exercise;

    @BeforeEach
    public void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(2, 2, 2);

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
        request.getList("/api/exercises/" + exercise.getId() + "/hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void queryAllHintsForAnExerciseAsATutor() throws Exception {
        request.getList("/api/exercises/" + exercise.getId() + "/hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void queryAllHintsForAnExerciseAsAnInstructor() throws Exception {
        request.getList("/api/exercises/" + exercise.getId() + "/hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getHintForAnExerciseAsAStudentShouldReturnForbidden() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/exercise-hints/" + exerciseHint.getId(), HttpStatus.FORBIDDEN, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getHintForAnExerciseAsATutor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getHintForAnExerciseAsAnInstructor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void createHintAsAStudentShouldReturnForbidden() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().title("title 4").content("content 4").exercise(exercise);
        request.post("/api/exercise-hints/", exerciseHint, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void createHintAsTutor() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().title("title 4").content("content 4").exercise(exercise);
        request.post("/api/exercise-hints/", exerciseHint, HttpStatus.CREATED);

        List<ExerciseHint> exerciseHints = exerciseHintRepository.findAll();
        assertThat(exerciseHints).hasSize(4);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().title("title 4").content("content 4").exercise(exercise);
        request.post("/api/exercise-hints/", exerciseHint, HttpStatus.CREATED);

        List<ExerciseHint> exerciseHints = exerciseHintRepository.findAll();
        assertThat(exerciseHints).hasSize(4);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updateHintAsAStudentShouldReturnForbidden() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        String newContent = "new content value!";
        String contentBefore = exerciseHint.getContent();

        exerciseHint.setContent(newContent);
        request.put("/api/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.FORBIDDEN);

        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
        assertThat(hintAfterSave.get().getContent()).isEqualTo(contentBefore);

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void updateHintAsTutor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        String newContent = "new content value!";

        exerciseHint.setContent(newContent);
        request.put("/api/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.OK);

        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
        assertThat(hintAfterSave.get().getContent()).isEqualTo(newContent);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        String newContent = "new content value!";

        exerciseHint.setContent(newContent);
        request.put("/api/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.OK);

        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
        assertThat(hintAfterSave.get().getContent()).isEqualTo(newContent);
    }

}
