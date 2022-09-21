package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;

class CodeHintIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private CodeHintRepository codeHintRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    private ProgrammingExercise exercise;

    private CodeHint codeHint;

    private ProgrammingExerciseSolutionEntry solutionEntry;

    @BeforeEach
    void initTestCase() {
        final Course course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(1, 1, 1, 1);

        exercise = (ProgrammingExercise) course.getExercises().stream().findAny().orElseThrow();
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
        codeHint = codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHintRepository.findByExerciseId(exercise.getId()).stream().findAny().orElseThrow().getId());
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

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateSolutionEntriesOnSaving() throws Exception {
        addCodeHints();
        var solutionEntries = codeHint.getSolutionEntries().stream().toList();

        var testCases = testCaseRepository.findByExerciseId(exercise.getId());

        var changedEntry = solutionEntries.get(0);
        changedEntry.setLine(100);
        changedEntry.setPreviousLine(200);
        changedEntry.setCode("Changed code");
        changedEntry.setPreviousCode("Changed previous code");
        changedEntry.setTestCase(testCases.stream().toList().get(2));

        var newEntry = new ProgrammingExerciseSolutionEntry();
        newEntry.setLine(200);
        newEntry.setPreviousLine(300);
        newEntry.setCode("New code");
        newEntry.setPreviousCode("New previous code");
        var testCase = testCases.stream().toList().get(0);
        newEntry.setTestCase(testCase);
        var savedNewEntry = solutionEntryRepository.save(newEntry);
        savedNewEntry.setTestCase(testCase);
        codeHint.setSolutionEntries(new HashSet<>(Set.of(changedEntry, savedNewEntry)));

        request.put("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/" + codeHint.getId(), codeHint, HttpStatus.OK);

        var updatedHint = codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHint.getId());
        assertThat(updatedHint.getSolutionEntries()).containsExactly(changedEntry, savedNewEntry);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAllCodeHints() throws Exception {
        addCodeHints();

        var actualCodeHints = request.getList("/api/programming-exercises/" + exercise.getId() + "/code-hints", HttpStatus.OK, CodeHint.class);
        assertThat(actualCodeHints).hasSize(3);
    }
}
