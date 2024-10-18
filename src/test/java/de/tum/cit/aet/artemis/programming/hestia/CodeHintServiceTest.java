package de.tum.cit.aet.artemis.programming.hestia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.hestia.CodeHint;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseTestCaseType;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
class CodeHintServiceTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "codehintservice";

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    private ProgrammingExerciseTestCase addTestCaseToExercise(String name) {
        var testCase = new ProgrammingExerciseTestCase();
        testCase.setTestName(name);
        testCase.setExercise(exercise);
        testCase.setVisibility(Visibility.ALWAYS);
        testCase.setActive(true);
        testCase.setWeight(1D);
        testCase.setType(ProgrammingExerciseTestCaseType.BEHAVIORAL);
        return testCaseRepository.save(testCase);
    }

    private ProgrammingExerciseSolutionEntry addSolutionEntryToTestCase(ProgrammingExerciseTestCase testCase) {
        var solutionEntry = new ProgrammingExerciseSolutionEntry();
        solutionEntry.setTestCase(testCase);
        solutionEntry.setLine(1);
        solutionEntry.setCode("code");
        return programmingExerciseSolutionEntryRepository.save(solutionEntry);
    }

    private ProgrammingExerciseTask addTaskToExercise(String name, List<ProgrammingExerciseTestCase> testCases) {
        var task = new ProgrammingExerciseTask();
        task.setExercise(exercise);
        task.setTaskName(name);
        task = taskRepository.save(task);
        for (int i = 0; i < testCases.size(); i++) {
            ProgrammingExerciseTestCase testCase = testCases.get(i);
            testCase.getTasks().add(task);
            testCases.set(i, testCaseRepository.save(testCase));
        }
        task.setTestCases(new HashSet<>(testCases));
        return task;
    }

    private CodeHint addCodeHintToTask(String name, ProgrammingExerciseTask task, Set<ProgrammingExerciseSolutionEntry> solutionEntries) {
        var codeHint = new CodeHint();
        codeHint.setTitle(name);
        codeHint.setProgrammingExerciseTask(task);
        codeHint.setExercise(exercise);
        codeHint.setSolutionEntries(solutionEntries);

        solutionEntries.forEach(entry -> entry.setCodeHint(codeHint));
        var createdHint = codeHintRepository.save(codeHint);
        programmingExerciseSolutionEntryRepository.saveAll(solutionEntries);
        return createdHint;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerationWithNoSolutionEntry() {
        var testCase = addTestCaseToExercise("TestCase1");
        addTaskToExercise("Task1", Arrays.asList(testCase));

        var codeHints = codeHintService.generateCodeHintsForExercise(exercise, true);
        assertThat(codeHints).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerationWithOneSolutionEntry() {
        var testCase = addTestCaseToExercise("TestCase1");
        var solutionEntry = addSolutionEntryToTestCase(testCase);
        var task = addTaskToExercise("Task1", Arrays.asList(testCase));

        var codeHints = codeHintService.generateCodeHintsForExercise(exercise, true);
        assertThat(codeHints).hasSize(1);
        assertThat(codeHints.getFirst().getProgrammingExerciseTask()).isEqualTo(task);
        assertThat(codeHints.getFirst().getSolutionEntries()).containsExactly(solutionEntry);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerationTwiceShouldDeleteOldHint() {
        var testCase = addTestCaseToExercise("TestCase1");
        var solutionEntry = addSolutionEntryToTestCase(testCase);
        var task = addTaskToExercise("Task1", Arrays.asList(testCase));

        var codeHints = codeHintService.generateCodeHintsForExercise(exercise, true);
        assertThat(codeHints).hasSize(1);
        var codeHint = codeHints.getFirst();

        codeHints = codeHintService.generateCodeHintsForExercise(exercise, true);
        assertThat(codeHints).hasSize(1);
        assertThat(codeHints.getFirst()).isNotEqualTo(codeHint);
        assertThat(codeHints.getFirst().getProgrammingExerciseTask()).isEqualTo(task);
        assertThat(codeHints.getFirst().getSolutionEntries()).containsExactly(solutionEntry);

        final Set<CodeHint> codeHintsAfterSaving = codeHintRepository.findByExerciseId(exercise.getId());
        assertThat(codeHintsAfterSaving).hasSize(1);
        assertThat(codeHintsAfterSaving.stream().findAny().orElseThrow()).isNotEqualTo(codeHint).isEqualTo(codeHints.getFirst());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerationTwiceShouldNotDeleteOldHint() {
        var testCase = addTestCaseToExercise("TestCase1");
        var solutionEntry = addSolutionEntryToTestCase(testCase);
        var task = addTaskToExercise("Task1", Arrays.asList(testCase));

        var codeHints = codeHintService.generateCodeHintsForExercise(exercise, false);
        assertThat(codeHints).hasSize(1);
        var codeHint = codeHints.getFirst();

        codeHints = codeHintService.generateCodeHintsForExercise(exercise, false);
        assertThat(codeHints).hasSize(1);
        assertThat(codeHints.getFirst()).isNotEqualTo(codeHint);
        assertThat(codeHints.getFirst().getProgrammingExerciseTask()).isEqualTo(task);
        assertThat(codeHints.getFirst().getSolutionEntries()).containsExactly(solutionEntry);
        assertThat(codeHintRepository.findByExerciseId(exercise.getId())).containsExactlyInAnyOrder(codeHint, codeHints.getFirst());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateTestCaseOfSolutionEntry() {
        var testCase1 = addTestCaseToExercise("testCase1");
        var testCase2 = addTestCaseToExercise("testCase2");
        var entry = addSolutionEntryToTestCase(testCase1);
        var task = addTaskToExercise("task", new ArrayList<>(List.of(testCase1, testCase2)));
        var codeHint = addCodeHintToTask("codeHint1", task, new HashSet<>(Set.of(entry)));

        var entryToUpdate = codeHint.getSolutionEntries().stream().findFirst().orElseThrow();
        entryToUpdate.setTestCase(testCase2);
        codeHintService.updateSolutionEntriesForCodeHint(codeHint);

        var allEntries = programmingExerciseSolutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());
        assertThat(allEntries).hasSize(1);
        assertThat(allEntries.stream().findAny().orElseThrow().getTestCase().getId()).isEqualTo(testCase2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdatedContentOfSolutionEntry() {
        var testCase1 = addTestCaseToExercise("testCase");
        var entry = addSolutionEntryToTestCase(testCase1);
        var task = addTaskToExercise("task", new ArrayList<>(List.of(testCase1)));
        var codeHint = addCodeHintToTask("codeHint", task, new HashSet<>(Set.of(entry)));

        var entryToUpdate = codeHint.getSolutionEntries().stream().findFirst().orElseThrow();
        entryToUpdate.setLine(120);
        entry.setPreviousLine(130);
        entryToUpdate.setCode("Updated code");
        entry.setPreviousCode("Updated previous code");
        entry.setFilePath("Updated file path");
        codeHintService.updateSolutionEntriesForCodeHint(codeHint);

        var allEntries = programmingExerciseSolutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());
        assertThat(allEntries).hasSize(1);
        assertThat(allEntries.stream().findAny().orElseThrow()).isEqualTo(entryToUpdate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveWithNewSolutionEntry() {
        // the entry has been created and persisted, but not assigned to the hint yet
        var testCase = addTestCaseToExercise("testCase");
        var manuallyCreatedEntry = addSolutionEntryToTestCase(testCase);
        var task = addTaskToExercise("task", new ArrayList<>(List.of(testCase)));
        var codeHint = addCodeHintToTask("codeHint", task, new HashSet<>(Collections.emptySet()));

        codeHint.setSolutionEntries(new HashSet<>(Set.of(manuallyCreatedEntry)));
        codeHintService.updateSolutionEntriesForCodeHint(codeHint);

        var allEntries = programmingExerciseSolutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());
        assertThat(allEntries).containsExactly(manuallyCreatedEntry);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveWithRemovedSolutionEntry() {
        // the entry has been created and persisted, but not assigned to the hint yet
        var testCase = addTestCaseToExercise("testCase");
        var entryToRemove = addSolutionEntryToTestCase(testCase);
        var task = addTaskToExercise("task", new ArrayList<>(List.of(testCase)));
        var codeHint = addCodeHintToTask("codeHint", task, new HashSet<>(Set.of(entryToRemove)));

        codeHint.setSolutionEntries(new HashSet<>(Collections.emptySet()));
        codeHintService.updateSolutionEntriesForCodeHint(codeHint);

        var entriesForHint = programmingExerciseSolutionEntryRepository.findByCodeHintId(codeHint.getId());
        assertThat(entriesForHint).isEmpty();

        var allEntries = programmingExerciseSolutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());
        assertThat(allEntries).containsExactly(entryToRemove);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSaveEntryWithTestCaseUnrelatedToHintTask() {
        // the test case of an entry belongs to a task unequal to the task of the hint that is updated
        var unrelatedTestCase = addTestCaseToExercise("unrelatedTaskTestCase");
        addTaskToExercise("unrelatedTask", new ArrayList<>(List.of(unrelatedTestCase)));
        var invalidSolutionEntry = new ProgrammingExerciseSolutionEntry();
        invalidSolutionEntry.setTestCase(unrelatedTestCase);
        invalidSolutionEntry.setCode("abc");

        var relatedTestCase = addTestCaseToExercise("relatedTaskTestCase");
        var relatedTask = addTaskToExercise("relatedTask", new ArrayList<>(List.of(relatedTestCase)));
        var codeHint = addCodeHintToTask("codeHint", relatedTask, new HashSet<>(Collections.emptySet()));

        codeHint.setSolutionEntries(new HashSet<>(Set.of(invalidSolutionEntry)));
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> codeHintService.updateSolutionEntriesForCodeHint(codeHint));

        var entriesForHint = programmingExerciseSolutionEntryRepository.findByCodeHintId(codeHint.getId());
        assertThat(entriesForHint).isEmpty();

        var allEntries = programmingExerciseSolutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());
        assertThat(allEntries).isEmpty();
    }
}
