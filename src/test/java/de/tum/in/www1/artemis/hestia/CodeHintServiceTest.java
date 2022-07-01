package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.hestia.CodeHintService;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
class CodeHintServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CodeHintService codeHintService;

    @Autowired
    private CodeHintRepository codeHintRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository taskRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(0, 0, 0, 1);
        database.addCourseWithOneProgrammingExercise();
        exercise = programmingExerciseRepository.findAll().get(0);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
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
        solutionEntry.setCode(UUID.randomUUID().toString());
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

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGenerationWithNoSolutionEntry() {
        var testCase = addTestCaseToExercise("TestCase1");
        addTaskToExercise("Task1", Arrays.asList(testCase));

        var codeHints = codeHintService.generateCodeHintsForExercise(exercise, true);
        assertThat(codeHints).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
        assertThat(codeHintRepository.findAll()).hasSize(1);
        assertThat(codeHintRepository.findAll().get(0)).isNotEqualTo(codeHint).isEqualTo(codeHints.get(0));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
        assertThat(codeHintRepository.findAll()).containsExactlyInAnyOrder(codeHint, codeHints.get(0));
    }
}
