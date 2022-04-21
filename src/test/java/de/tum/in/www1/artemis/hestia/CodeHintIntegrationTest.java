package de.tum.in.www1.artemis.hestia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;

public class CodeHintIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    private Exercise exercise;

    @BeforeEach
    public void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(1, 1, 1, 1);

        exercise = exerciseRepository.findAll().get(0);
        database.addHintsToExercise(exercise);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void generateCodeHintsForAnExerciseAsAStudent() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void generateCodeHintsForAnExerciseAsATutor() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void generateCodeHintsForAnExerciseAsAnEditor() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void generateCodeHintsForAnExerciseAsAnInstructor() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.OK);
    }
}
