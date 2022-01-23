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
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.repository.*;

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
        var task = new ProgrammingExerciseTask().taskName(taskName).exercise(programmingExerciseRepository.getById(programmingExerciseId));
        task = programmingExerciseTaskRepository.save(task);
        return task;
    }

    ProgrammingExerciseSolutionEntry[] addSolutionEntriesToTestCase(int count, ProgrammingExerciseTestCase testCase) {
        var solutionEntries = new ProgrammingExerciseSolutionEntry[count];
        for (int i = 0; i < count; i++) {
            var solutionEntry = new ProgrammingExerciseSolutionEntry().testCase(testCase).code("Code block 1").line(i);
            solutionEntry = programmingExerciseSolutionEntryRepository.save(solutionEntry);
            solutionEntries[i] = solutionEntry;
        }
        return solutionEntries;
    }

    @Test
    void addOneTaskToProgrammingExercise() {
        var task = addTaskToProgrammingExercise("Task 1");
        assertThat(programmingExerciseTaskRepository.findByExerciseId(programmingExerciseId)).containsExactly(task);
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
            assertThat(programmingExerciseSolutionEntryRepository.findByTestCaseId(testCase.getId())).hasSize(2).containsExactly(solutionEntries);
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
        assertThat(programmingExerciseTestCaseRepository.findByExerciseId(programmingExerciseId)).hasSize(3).isEqualTo(testCases);
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
            assertThat(programmingExerciseSolutionEntryRepository.findByTestCaseId(testCase.getId())).hasSize(2).containsExactly(solutionEntries);
            allSolutionEntries.addAll(List.of(solutionEntries));
        }
        var codeHint = (CodeHint) new CodeHint().programmingExerciseTask(task).exercise(programmingExercise).title("Code Hint 1");
        codeHint = codeHintRepository.save(codeHint);
        for (ProgrammingExerciseSolutionEntry solutionEntry : allSolutionEntries) {
            solutionEntry.setCodeHint(codeHint);
            programmingExerciseSolutionEntryRepository.save(solutionEntry);
        }
        assertThat(programmingExerciseSolutionEntryRepository.findByCodeHintId(codeHint.getId())).hasSize(6).isEqualTo(allSolutionEntries);
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
