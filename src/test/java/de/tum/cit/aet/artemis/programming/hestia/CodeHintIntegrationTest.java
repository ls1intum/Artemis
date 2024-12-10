package de.tum.cit.aet.artemis.programming.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;

class CodeHintIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "codehint";

    private ProgrammingExercise exercise;

    private CodeHint codeHint;

    private ProgrammingExerciseSolutionEntry solutionEntry;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void generateCodeHintsForAnExerciseAsAStudent() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void generateCodeHintsForAnExerciseAsATutor() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void generateCodeHintsForAnExerciseAsAnEditor() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generateCodeHintsForAnExerciseAsAnInstructor() throws Exception {
        request.postListWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints", null, CodeHint.class, HttpStatus.OK);
    }

    private void addCodeHints() {
        exercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(exercise);
        programmingExerciseUtilService.addHintsToExercise(exercise);
        programmingExerciseUtilService.addTasksToProgrammingExercise(exercise);
        programmingExerciseUtilService.addSolutionEntriesToProgrammingExercise(exercise);
        programmingExerciseUtilService.addCodeHintsToProgrammingExercise(exercise);
        Set<CodeHint> hints = codeHintRepository.findByExerciseId(exercise.getId());
        codeHint = hints.stream().filter(hint -> "Task for test1".equals(hint.getProgrammingExerciseTask().getTaskName())).findFirst().orElseThrow();
        codeHint = codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHint.getId());
        solutionEntry = codeHint.getSolutionEntries().stream().findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void removeSolutionEntryFromCodeHintAsAStudent() throws Exception {
        addCodeHints();
        request.delete("/api/programming-exercises/" + exercise.getId() + "/code-hints/" + codeHint.getId() + "/solution-entries/" + solutionEntry.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void removeSolutionEntryFromCodeHintAsATutor() throws Exception {
        addCodeHints();
        request.delete("/api/programming-exercises/" + exercise.getId() + "/code-hints/" + codeHint.getId() + "/solution-entries/" + solutionEntry.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void removeSolutionEntryFromCodeHintAsAnEditor() throws Exception {
        addCodeHints();
        request.delete("/api/programming-exercises/" + exercise.getId() + "/code-hints/" + codeHint.getId() + "/solution-entries/" + solutionEntry.getId(), HttpStatus.NO_CONTENT);
        assertThat(codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHint.getId()).getSolutionEntries()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void removeSolutionEntryFromCodeHintAsAnInstructor() throws Exception {
        addCodeHints();
        request.delete("/api/programming-exercises/" + exercise.getId() + "/code-hints/" + codeHint.getId() + "/solution-entries/" + solutionEntry.getId(), HttpStatus.NO_CONTENT);
        assertThat(codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHint.getId()).getSolutionEntries()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateSolutionEntriesOnSaving() throws Exception {
        addCodeHints();
        var solutionEntries = codeHint.getSolutionEntries().stream().toList();

        Map<String, ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(exercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, test -> test));

        var changedEntry = solutionEntries.getFirst();
        changedEntry.setLine(100);
        changedEntry.setPreviousLine(200);
        changedEntry.setCode("Changed code");
        changedEntry.setPreviousCode("Changed previous code");
        changedEntry.setTestCase(testCases.get("test3"));

        var newEntry = new ProgrammingExerciseSolutionEntry();
        newEntry.setLine(200);
        newEntry.setPreviousLine(300);
        newEntry.setCode("New code");
        newEntry.setPreviousCode("New previous code");
        var testCase = testCases.get("test1");
        newEntry.setTestCase(testCase);
        var savedNewEntry = programmingExerciseSolutionEntryRepository.save(newEntry);
        savedNewEntry.setTestCase(testCase);
        codeHint.setSolutionEntries(new HashSet<>(Set.of(changedEntry, savedNewEntry)));

        request.put("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/" + codeHint.getId(), codeHint, HttpStatus.OK);

        var updatedHint = codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHint.getId());
        assertThat(updatedHint.getSolutionEntries()).containsExactlyInAnyOrder(changedEntry, savedNewEntry);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllCodeHints() throws Exception {
        addCodeHints();

        var actualCodeHints = request.getList("/api/programming-exercises/" + exercise.getId() + "/code-hints", HttpStatus.OK, CodeHint.class);
        assertThat(actualCodeHints).hasSize(3);
    }
}
