package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.*;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.hestia.CodeHintService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
class CodeHintServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "codehintservice";

    @Autowired
    private CodeHintService codeHintService;

    @Autowired
    private CodeHintRepository codeHintRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository taskRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

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
        return solutionEntryRepository.save(solutionEntry);
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
        solutionEntryRepository.saveAll(solutionEntries);
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
        assertThat(codeHints.get(0).getProgrammingExerciseTask()).isEqualTo(task);
        assertThat(codeHints.get(0).getSolutionEntries()).containsExactly(solutionEntry);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerationTwiceShouldDeleteOldHint() {
        var testCase = addTestCaseToExercise("TestCase1");
        var solutionEntry = addSolutionEntryToTestCase(testCase);
        var task = addTaskToExercise("Task1", Arrays.asList(testCase));

        var codeHints = codeHintService.generateCodeHintsForExercise(exercise, true);
        assertThat(codeHints).hasSize(1);
        var codeHint = codeHints.get(0);

        codeHints = codeHintService.generateCodeHintsForExercise(exercise, true);
        assertThat(codeHints).hasSize(1);
        assertThat(codeHints.get(0)).isNotEqualTo(codeHint);
        assertThat(codeHints.get(0).getProgrammingExerciseTask()).isEqualTo(task);
        assertThat(codeHints.get(0).getSolutionEntries()).containsExactly(solutionEntry);

        final Set<CodeHint> codeHintsAfterSaving = codeHintRepository.findByExerciseId(exercise.getId());
        assertThat(codeHintsAfterSaving).hasSize(1);
        assertThat(codeHintsAfterSaving.stream().findAny().orElseThrow()).isNotEqualTo(codeHint).isEqualTo(codeHints.get(0));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerationTwiceShouldNotDeleteOldHint() {
        var testCase = addTestCaseToExercise("TestCase1");
        var solutionEntry = addSolutionEntryToTestCase(testCase);
        var task = addTaskToExercise("Task1", Arrays.asList(testCase));

        var codeHints = codeHintService.generateCodeHintsForExercise(exercise, false);
        assertThat(codeHints).hasSize(1);
        var codeHint = codeHints.get(0);

        codeHints = codeHintService.generateCodeHintsForExercise(exercise, false);
        assertThat(codeHints).hasSize(1);
        assertThat(codeHints.get(0)).isNotEqualTo(codeHint);
        assertThat(codeHints.get(0).getProgrammingExerciseTask()).isEqualTo(task);
        assertThat(codeHints.get(0).getSolutionEntries()).containsExactly(solutionEntry);
        assertThat(codeHintRepository.findByExerciseId(exercise.getId())).containsExactlyInAnyOrder(codeHint, codeHints.get(0));
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

        var allEntries = solutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());
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

        var allEntries = solutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());
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

        var allEntries = solutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());
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

        var entriesForHint = solutionEntryRepository.findByCodeHintId(codeHint.getId());
        assertThat(entriesForHint).isEmpty();

        var allEntries = solutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());
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

        var entriesForHint = solutionEntryRepository.findByCodeHintId(codeHint.getId());
        assertThat(entriesForHint).isEmpty();

        var allEntries = solutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());
        assertThat(allEntries).isEmpty();
    }
}
