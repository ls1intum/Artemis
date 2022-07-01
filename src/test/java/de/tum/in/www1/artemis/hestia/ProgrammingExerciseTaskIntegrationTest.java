package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

class ProgrammingExerciseTaskIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository;

    @Autowired
    private ProgrammingExerciseTaskService programmingExerciseTaskService;

    private ProgrammingExercise programmingExercise;

    private Set<ProgrammingExerciseTestCase> testCases;

    @BeforeEach
    void initTestCases() {
        database.addCourseWithOneProgrammingExerciseAndSpecificTestCases();
        database.addUsers(2, 2, 1, 2);

        programmingExercise = programmingExerciseRepository.findAll().get(0);
        this.testCases = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId());
        for (ProgrammingExerciseTestCase testCase : testCases) {
            var solutionEntry = new ProgrammingExerciseSolutionEntry();
            solutionEntry.setTestCase(testCase);
            solutionEntry.setPreviousCode("No code");
            solutionEntry.setCode("Some code");
            solutionEntry.setPreviousLine(1);
            solutionEntry.setCodeHint(null);
            solutionEntry.setLine(1);
            solutionEntry.setFilePath("code.java");
            programmingExerciseSolutionEntryRepository.save(solutionEntry);
        }
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testDeletionAsStudent() throws Exception {
        request.delete("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testDeletionAsTutor() throws Exception {
        request.delete("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testDeletionAsEditor() throws Exception {
        request.delete("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteAllTasksAndSolutionEntriesForProgrammingExercise() throws Exception {
        Set<Long> solutionEntryIdsBeforeDeleting = testCases.stream().map(ProgrammingExerciseTestCase::getSolutionEntries).flatMap(Collection::stream).map(DomainObject::getId)
                .collect(Collectors.toSet());

        ProgrammingExerciseTask task = new ProgrammingExerciseTask();
        task.setExercise(programmingExercise);
        task.setTaskName("Task");
        task.setTestCases(new HashSet<>(testCases));
        programmingExerciseTaskRepository.save(task);

        request.delete("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.NO_CONTENT);
        assertThat(programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId())).isEmpty();
        assertThat(programmingExerciseSolutionEntryRepository.findAllById(solutionEntryIdsBeforeDeleting)).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testTaskExtractionAsStudent() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.FORBIDDEN, Set.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTaskExtractionForProgrammingExercise() throws Exception {
        String taskName1 = "Implement Bubble Sort";
        String taskName2 = "Implement Policy and Context";
        programmingExercise.setProblemStatement(
                "# Sorting with the Strategy Pattern\n" + "\n" + "In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.\n"
                        + "\n" + "### Part 1: Sorting\n" + "\n" + "First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.\n" + "\n"
                        + "**You have the following tasks:**\n" + "\n" + "1. [task][" + taskName1 + "](testClass[BubbleSort])\n" + "Implement the class `BubbleSort`.\n"
                        + "2. [task][" + taskName2 + "](testMethods[Context],testMethods[Policy],)\n" + "Implement the classes `Context` and `Policy`. Make sure to follow..");
        programmingExerciseRepository.save(programmingExercise);
        programmingExerciseTaskService.updateTasksFromProblemStatement(programmingExercise);

        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.OK, Set.class);
        Set<ProgrammingExerciseTask> extractedTasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCaseAndSolutionEntriesElseThrow(programmingExercise.getId());
        Optional<ProgrammingExerciseTask> task1Optional = extractedTasks.stream().filter(task -> task.getTaskName().equals(taskName1)).findFirst();
        Optional<ProgrammingExerciseTask> task2Optional = extractedTasks.stream().filter(task -> task.getTaskName().equals(taskName2)).findFirst();
        assertThat(task1Optional).isPresent();
        assertThat(task2Optional).isPresent();
        ProgrammingExerciseTask task1 = task1Optional.get();
        ProgrammingExerciseTask task2 = task2Optional.get();

        Set<ProgrammingExerciseTestCase> expectedTestCasesForTask1 = new HashSet<>();
        expectedTestCasesForTask1.add(testCases.stream().filter(testCase -> "testClass[BubbleSort]".equals(testCase.getTestName())).findFirst().orElseThrow());
        Set<ProgrammingExerciseTestCase> expectedTestCasesForTask2 = new HashSet<>();
        expectedTestCasesForTask2.add(testCases.stream().filter(testCase -> "testMethods[Context]".equals(testCase.getTestName())).findFirst().orElseThrow());
        expectedTestCasesForTask2.add(testCases.stream().filter(testCase -> "testMethods[Policy]".equals(testCase.getTestName())).findFirst().orElseThrow());
        assertThat(task1.getTestCases()).isEqualTo(expectedTestCasesForTask1);
        assertThat(task2.getTestCases()).isEqualTo(expectedTestCasesForTask2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTaskExtractionForEmptyProblemStatement() throws Exception {
        programmingExercise.setProblemStatement("");
        programmingExerciseRepository.save(programmingExercise);

        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.OK, Set.class);

        assertThat(programmingExerciseTaskRepository.findAll()).isEmpty();
    }
}
