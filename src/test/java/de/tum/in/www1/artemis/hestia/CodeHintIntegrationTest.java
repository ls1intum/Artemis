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
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;

class CodeHintIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private CodeHintRepository codeHintRepository;

    private ProgrammingExercise exercise;

    private CodeHint codeHint;

    private ProgrammingExerciseSolutionEntry solutionEntry;

    @BeforeEach
    void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(1, 1, 1, 1);

        exercise = exerciseRepository.findAll().get(0);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void generateCodeHintsForAnExerciseAsAStudent() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void generateCodeHintsForAnExerciseAsATutor() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void generateCodeHintsForAnExerciseAsAnEditor() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void generateCodeHintsForAnExerciseAsAnInstructor() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.OK);
    }

    private void addCodeHints() {
        exercise = database.loadProgrammingExerciseWithEagerReferences(exercise);
        database.addHintsToExercise(exercise);
        database.addTasksToProgrammingExercise(exercise);
        database.addSolutionEntriesToProgrammingExercise(exercise);
        database.addCodeHintsToProgrammingExercise(exercise);
        codeHint = codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHintRepository.findAll().get(0).getId());
        solutionEntry = codeHint.getSolutionEntries().stream().findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void removeSolutionEntryFromCodeHintAsAStudent() throws Exception {
        addCodeHints();
        request.delete("/api/programming-exercises/" + exercise.getId() + "/code-hints/" + codeHint.getId() + "/solution-entries/" + solutionEntry.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void removeSolutionEntryFromCodeHintAsATutor() throws Exception {
        addCodeHints();
        request.delete("/api/programming-exercises/" + exercise.getId() + "/code-hints/" + codeHint.getId() + "/solution-entries/" + solutionEntry.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void removeSolutionEntryFromCodeHintAsAnEditor() throws Exception {
        addCodeHints();
        request.delete("/api/programming-exercises/" + exercise.getId() + "/code-hints/" + codeHint.getId() + "/solution-entries/" + solutionEntry.getId(), HttpStatus.NO_CONTENT);
        assertThat(codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHint.getId()).getSolutionEntries()).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void removeSolutionEntryFromCodeHintAsAnInstructor() throws Exception {
        addCodeHints();
        request.delete("/api/programming-exercises/" + exercise.getId() + "/code-hints/" + codeHint.getId() + "/solution-entries/" + solutionEntry.getId(), HttpStatus.NO_CONTENT);
        assertThat(codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHint.getId()).getSolutionEntries()).isEmpty();
    }
}
