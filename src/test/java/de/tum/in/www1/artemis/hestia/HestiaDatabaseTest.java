package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;

/**
 * This class tests the database relations of the Hestia domain models.
 * This currently includes ProgrammingExerciseTask, ProgrammingExerciseSolutionEntry and CodeHint.
 * It tests if the addition and deletion of these models works as expected.
 */
public class HestiaDatabaseTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Autowired
    private ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository;

    @Autowired
    private CodeHintRepository codeHintRepository;

    private Long programmingExerciseId;

    @BeforeEach
    void init() {
        database.addUsers(2, 2, 0, 2);
        database.addCourseWithOneProgrammingExercise();
        programmingExerciseId = programmingExerciseRepository.findAll().get(0).getId();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    ProgrammingExerciseTask addTaskToProgrammingExercise(String taskName) {
        var task = new ProgrammingExerciseTask();
        task.setTaskName(taskName);
        task.setExercise(programmingExerciseRepository.getById(programmingExerciseId));
        task = programmingExerciseTaskRepository.save(task);
        return task;
    }

    ProgrammingExerciseSolutionEntry[] addSolutionEntriesToTestCase(int count, ProgrammingExerciseTestCase testCase) {
        var solutionEntries = new ProgrammingExerciseSolutionEntry[count];
        for (int i = 0; i < count; i++) {
            var solutionEntry = new ProgrammingExerciseSolutionEntry();
            solutionEntry.setTestCase(testCase);
            solutionEntry.setCode("Code block 1");
            solutionEntry.setLine(i);
            solutionEntry = programmingExerciseSolutionEntryRepository.save(solutionEntry);
            solutionEntries[i] = solutionEntry;
        }
        return solutionEntries;
    }

    @Test
    void addOneTaskToProgrammingExercise() {
        var task = addTaskToProgrammingExercise("Task 1");
        assertThat(programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExerciseId)).containsExactly(task);
    }

    @Test
    void deleteProgrammingExerciseWithTask() {
        addOneTaskToProgrammingExercise();
        programmingExerciseRepository.deleteById(programmingExerciseId);
        assertThat(programmingExerciseTaskRepository.findAll()).isEmpty();
    }

    @Test
    void addTestCasesWithSolutionEntriesToProgrammingExercise() {
        var programmingExercise = programmingExerciseRepository.getById(programmingExerciseId);
        database.addTestCasesToProgrammingExercise(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExerciseId);
        assertThat(testCases).isNotEmpty();
        for (ProgrammingExerciseTestCase testCase : testCases) {
            var solutionEntries = addSolutionEntriesToTestCase(2, testCase);
            assertThat(programmingExerciseSolutionEntryRepository.findByTestCaseId(testCase.getId())).containsExactly(solutionEntries);
        }
    }

    @Test
    void deleteProgrammingExerciseWithTestCasesAndSolutionEntries() {
        addTestCasesWithSolutionEntriesToProgrammingExercise();
        programmingExerciseRepository.deleteById(programmingExerciseId);
        assertThat(programmingExerciseTestCaseRepository.findAll()).isEmpty();
        assertThat(programmingExerciseSolutionEntryRepository.findAll()).isEmpty();
    }

    @Test
    void deleteTaskWithTestCases() {
        var programmingExercise = programmingExerciseRepository.getById(programmingExerciseId);
        database.addTestCasesToProgrammingExercise(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExerciseId);
        assertThat(testCases).isNotEmpty();
        var task = addTaskToProgrammingExercise("Task 1");
        task.setTestCases(testCases);
        task = programmingExerciseTaskRepository.save(task);
        programmingExerciseTaskRepository.delete(task);
        assertThat(programmingExerciseTestCaseRepository.findByExerciseId(programmingExerciseId)).isEqualTo(testCases);
    }

    @Test
    void addCodeHintToProgrammingExercise() {
        var programmingExercise = programmingExerciseRepository.getById(programmingExerciseId);
        database.addTestCasesToProgrammingExercise(programmingExercise);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExerciseId);
        assertThat(testCases).isNotEmpty();
        var task = addTaskToProgrammingExercise("Task 1");
        task.setTestCases(testCases);
        task = programmingExerciseTaskRepository.save(task);
        Set<ProgrammingExerciseSolutionEntry> allSolutionEntries = new HashSet<>();
        for (ProgrammingExerciseTestCase testCase : testCases) {
            var solutionEntries = addSolutionEntriesToTestCase(2, testCase);
            assertThat(programmingExerciseSolutionEntryRepository.findByTestCaseId(testCase.getId())).containsExactly(solutionEntries);
            allSolutionEntries.addAll(List.of(solutionEntries));
        }
        var codeHint = (CodeHint) new CodeHint();
        codeHint.setProgrammingExerciseTask(task);
        codeHint.setExercise(programmingExercise);
        codeHint.setTitle("Code Hint 1");
        codeHint = codeHintRepository.save(codeHint);
        for (ProgrammingExerciseSolutionEntry solutionEntry : allSolutionEntries) {
            solutionEntry.setCodeHint(codeHint);
            programmingExerciseSolutionEntryRepository.save(solutionEntry);
        }
        codeHint.setSolutionEntries(allSolutionEntries);
        codeHint = codeHintRepository.save(codeHint);
        task.setCodeHints(Set.of(codeHint));
        programmingExerciseTaskRepository.save(task);
        assertThat(programmingExerciseSolutionEntryRepository.findByCodeHintId(codeHint.getId())).isEqualTo(allSolutionEntries);
        assertThat(codeHintRepository.findByExerciseId(programmingExerciseId)).containsExactly(codeHint);
    }

    @Test
    void deleteCodeHint() {
        addCodeHintToProgrammingExercise();
        var codeHint = codeHintRepository.findAll().get(0);
        codeHintRepository.delete(codeHint);
        assertThat(programmingExerciseTaskRepository.findByExerciseId(programmingExerciseId)).hasSize(1);
        assertThat(programmingExerciseSolutionEntryRepository.findAll()).hasSize(6);
    }

    @Test
    void deleteProgrammingExerciseWithCodeHint() {
        addCodeHintToProgrammingExercise();
        programmingExerciseRepository.deleteById(programmingExerciseId);
        assertThat(programmingExerciseTaskRepository.findAll()).isEmpty();
        assertThat(programmingExerciseSolutionEntryRepository.findAll()).isEmpty();
        assertThat(codeHintRepository.findAll()).isEmpty();
        assertThat(programmingExerciseTestCaseRepository.findAll()).isEmpty();
    }

    @Test
    void deleteTaskWithCodeHint() {
        addCodeHintToProgrammingExercise();
        var task = programmingExerciseTaskRepository.findAll().get(0);
        programmingExerciseTaskRepository.delete(task);
        assertThat(codeHintRepository.findAll()).isEmpty();
        assertThat(programmingExerciseTestCaseRepository.findAll()).hasSize(3);
        assertThat(programmingExerciseSolutionEntryRepository.findAll()).hasSize(6);
    }
}
