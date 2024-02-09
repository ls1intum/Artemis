package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;
import de.tum.in.www1.artemis.user.UserUtilService;

class ProgrammingExerciseTaskIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "progextask";

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

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    private ProgrammingExercise programmingExercise;

    private Set<ProgrammingExerciseTestCase> testCases;

    @BeforeEach
    void initTestCases() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndSpecificTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeletionAsStudent() throws Exception {
        request.delete("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeletionAsTutor() throws Exception {
        request.delete("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testDeletionAsEditor() throws Exception {
        request.delete("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTaskExtractionAsStudent() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.FORBIDDEN, Set.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTaskExtractionForProgrammingExercise() throws Exception {
        String taskName1 = "Implement Bubble Sort";
        String taskName2 = "Implement Policy and Context";
        programmingExercise.setProblemStatement(
                "# Sorting with the Strategy Pattern\n" + "\n" + "In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.\n"
                        + "\n" + "### Part 1: Sorting\n" + "\n" + "First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.\n" + "\n"
                        + "**You have the following tasks:**\n" + "\n" + "1. [task][" + taskName1 + "](testClass[BubbleSort])\n" + "Implement the class `BubbleSort`.\n"
                        + "2. [task][" + taskName2 + "](testMethods[Context],testMethods[Policy])\n" + "Implement the classes `Context` and `Policy`. Make sure to follow..");
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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTaskExtractionForEmptyProblemStatement() throws Exception {
        programmingExercise.setProblemStatement("");
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/tasks", HttpStatus.OK, Set.class);

        assertThat(programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTasksWithUnassignedTestCases_NoTasks() throws Exception {
        var response = request.getList("/api/programming-exercises/" + programmingExercise.getId() + "/tasks-with-unassigned-test-cases", HttpStatus.OK,
                ProgrammingExerciseTask.class);

        // No tasks available -> all tests in one "unassigned" group
        assertThat(response).hasSize(1);
        var unassigned = response.get(0);
        assertThat(unassigned.getTaskName()).isEqualTo("Not assigned to task");
        assertThat(unassigned.getTestCases()).hasSize(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTasksWithUnassignedTestCases_AllTasksAssigned() throws Exception {
        String taskName1 = "Implement Bubble Sort";
        String taskName2 = "Implement Policy and Context";
        programmingExercise.setProblemStatement("""
                # Sorting with the Strategy Pattern

                In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.

                ### Part 1: Sorting

                First, we need to implement two sorting algorithms, in this case `MergeSort` and `BubbleSort`.

                **You have the following tasks:**

                1. [task][%s](testClass[BubbleSort])
                Implement the class `BubbleSort`.
                2. [task][%s](testMethods[Context],testMethods[Policy])
                Implement the classes `Context` and `Policy`. Make sure to follow..
                """.formatted(taskName1, taskName2));
        programmingExerciseRepository.save(programmingExercise);
        programmingExerciseTaskService.updateTasksFromProblemStatement(programmingExercise);

        var response = request.getList("/api/programming-exercises/" + programmingExercise.getId() + "/tasks-with-unassigned-test-cases", HttpStatus.OK,
                ProgrammingExerciseTask.class);

        // 2 tasks, all test cases distributed across the tasks -> no unassigned
        assertThat(response).hasSize(2);
        var bubbleSort = response.stream().filter(task -> taskName1.equals(task.getTaskName())).findFirst().orElseThrow();
        var context = response.stream().filter(task -> taskName2.equals(task.getTaskName())).findFirst().orElseThrow();

        assertThat(bubbleSort.getTestCases()).hasSize(1).allMatch(tc -> "testClass[BubbleSort]".equals(tc.getTestName()));
        assertThat(context.getTestCases()).hasSize(2).anyMatch(tc -> "testMethods[Context]".equals(tc.getTestName()))
                .anyMatch(tc -> "testMethods[Policy]".equals(tc.getTestName()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTasksWithUnassignedTestCases_Mixed() throws Exception {
        String taskName = "Implement Bubble Sort";
        programmingExercise.setProblemStatement("[task][%s](testClass[BubbleSort])".formatted(taskName));
        programmingExerciseRepository.save(programmingExercise);
        programmingExerciseTaskService.updateTasksFromProblemStatement(programmingExercise);

        var response = request.getList("/api/programming-exercises/" + programmingExercise.getId() + "/tasks-with-unassigned-test-cases", HttpStatus.OK,
                ProgrammingExerciseTask.class);

        // 1 task, 1 unassigned
        assertThat(response).hasSize(2);
        var bubbleSort = response.stream().filter(task -> taskName.equals(task.getTaskName())).findFirst().orElseThrow();
        var unassigned = response.stream().filter(task -> "Not assigned to task".equals(task.getTaskName())).findFirst().orElseThrow();

        assertThat(bubbleSort.getTestCases()).hasSize(1).allMatch(tc -> "testClass[BubbleSort]".equals(tc.getTestName()));
        assertThat(unassigned.getTestCases()).hasSize(2).anyMatch(tc -> "testMethods[Context]".equals(tc.getTestName()))
                .anyMatch(tc -> "testMethods[Policy]".equals(tc.getTestName()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetTasksWithUnassignedTestCases_AsStudent() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/tasks-with-unassigned-test-cases", HttpStatus.FORBIDDEN, Set.class);
    }
}
