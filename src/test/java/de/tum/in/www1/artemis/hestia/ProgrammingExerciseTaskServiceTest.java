package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;
import de.tum.in.www1.artemis.user.UserUtilService;

class ProgrammingExerciseTaskServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "progextaskservice";

    @Autowired
    private ProgrammingExerciseTaskService programmingExerciseTaskService;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private CodeHintRepository codeHintRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndSpecificTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        updateProblemStatement("""
                [task][Task 1](testClass[BubbleSort])
                [task][Task 2](testMethods[Context])
                """);
    }

    private void updateProblemStatement(String problemStatement) {
        programmingExercise.setProblemStatement(problemStatement);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExerciseTaskService.updateTasksFromProblemStatement(programmingExercise);
    }

    @Test
    void testNewExercise() {
        assertThat(programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId())).hasSize(2);
        var tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId());
        assertThat(tasks).hasSize(2).anyMatch(programmingExerciseTask -> checkTaskEqual(programmingExerciseTask, "Task 1", "testClass[BubbleSort]"))
                .anyMatch(programmingExerciseTask -> checkTaskEqual(programmingExerciseTask, "Task 2", "testMethods[Context]"));
    }

    @Test
    void testAddTask() {
        var previousTaskIds = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId()).stream().map(ProgrammingExerciseTask::getId)
                .collect(Collectors.toSet());

        updateProblemStatement("""
                [task][Task 1](testClass[BubbleSort])
                [task][Task 2](testMethods[Context])
                [task][Task 3](testMethods[Policy])
                """);
        assertThat(programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId())).hasSize(3);
        var tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId());
        assertThat(tasks).hasSize(3).anyMatch(programmingExerciseTask -> checkTaskEqual(programmingExerciseTask, "Task 1", "testClass[BubbleSort]"))
                .anyMatch(programmingExerciseTask -> checkTaskEqual(programmingExerciseTask, "Task 2", "testMethods[Context]"))
                .anyMatch(programmingExerciseTask -> checkTaskEqual(programmingExerciseTask, "Task 3", "testMethods[Policy]"));

        // Test that the other tasks were not removed and re-added.
        var newTaskIds = tasks.stream().map(ProgrammingExerciseTask::getId).collect(Collectors.toSet());
        assertThat(newTaskIds).containsAll(previousTaskIds);
    }

    @Test
    void testRemoveAllTasks() {
        updateProblemStatement("Empty");
        assertThat(programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId())).isEmpty();
    }

    @Test
    void testReduceToOneTask() {
        updateProblemStatement("[task][Task 1](testClass[BubbleSort],testMethods[Context], testMethods[Policy])");
        assertThat(programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId())).hasSize(1);
        var tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId());
        assertThat(tasks).hasSize(1);
        var task = tasks.stream().findFirst().orElseThrow();
        assertThat(task.getTaskName()).isEqualTo("Task 1");
        assertThat(task.getTestCases()).hasSize(3);
        var expectedTestCaseNames = Set.of("testClass[BubbleSort]", "testMethods[Context]", "testMethods[Policy]");
        var actualTestCaseNames = task.getTestCases().stream().map(ProgrammingExerciseTestCase::getTestName).collect(Collectors.toSet());
        assertThat(actualTestCaseNames).isEqualTo(expectedTestCaseNames);
    }

    /**
     * Tests that renaming a task does not remove and read the task, but instead updates it
     */
    @Test
    void testRenameTask() {
        var previousTaskIds = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId()).stream().map(ProgrammingExerciseTask::getId)
                .collect(Collectors.toSet());

        updateProblemStatement("""
                [task][Task 1a](testClass[BubbleSort])
                [task][Task 2](testMethods[Context])
                """);

        assertThat(programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId())).hasSize(2);
        var tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId());

        var newTaskIds = tasks.stream().map(ProgrammingExerciseTask::getId).collect(Collectors.toSet());
        assertThat(previousTaskIds).isEqualTo(newTaskIds);

        assertThat(programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId())).isEqualTo(tasks);

        assertThat(tasks).anyMatch(programmingExerciseTask -> checkTaskEqual(programmingExerciseTask, "Task 1a", "testClass[BubbleSort]"))
                .anyMatch(programmingExerciseTask -> checkTaskEqual(programmingExerciseTask, "Task 2", "testMethods[Context]"));
    }

    /**
     * Tests that not changing any tasks in the problem statement will not update any tasks
     */
    @Test
    void testNoChanges() {
        var previousTaskIds = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId()).stream().map(ProgrammingExerciseTask::getId)
                .collect(Collectors.toSet());

        updateProblemStatement("""
                Test
                [task][Task 1](testClass[BubbleSort])
                [task][Task 2](testMethods[Context])
                """);

        assertThat(programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId())).hasSize(2);

        var newTaskIds = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId()).stream().map(ProgrammingExerciseTask::getId)
                .collect(Collectors.toSet());
        assertThat(previousTaskIds).isEqualTo(newTaskIds);
    }

    @Test
    void testDeleteWithCodeHints() {
        var task = programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId()).stream().filter(task1 -> "Task 1".equals(task1.getTaskName())).findFirst()
                .orElse(null);
        assertThat(task).isNotNull();

        var codeHint = new CodeHint();
        codeHint.setExercise(programmingExercise);
        codeHint.setProgrammingExerciseTask(task);
        codeHintRepository.save(codeHint);

        programmingExerciseTaskService.delete(task);
        assertThat(programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId())).hasSize(1);
        assertThat(programmingExerciseTaskRepository.findById(task.getId())).isEmpty();
        assertThat(codeHintRepository.findByExerciseId(programmingExercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getTasksWithoutInactiveFiltersOutInactive() {
        programmingExercise = programmingExerciseRepository
                .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxRepos(programmingExercise.getId()).orElseThrow();
        programmingExerciseTestCaseRepository.deleteAll(programmingExercise.getTestCases());

        String[] testCaseNames = { "testClass[BubbleSort]", "testParametrized(Parameter1, 2)[1]" };
        for (var name : testCaseNames) {
            var testCase = new ProgrammingExerciseTestCase();
            testCase.setExercise(programmingExercise);
            testCase.setTestName(name);
            testCase.setActive(true);
            programmingExerciseTestCaseRepository.save(testCase);
        }

        var testCase = new ProgrammingExerciseTestCase();
        testCase.setExercise(programmingExercise);
        testCase.setTestName("testWithBraces()");
        testCase.setActive(false);
        programmingExerciseTestCaseRepository.save(testCase);

        programmingExercise = programmingExerciseRepository
                .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxRepos(programmingExercise.getId()).orElseThrow();

        updateProblemStatement("""
                [task][Task 1](testClass[BubbleSort],testWithBraces(),testParametrized(Parameter1, 2)[1])
                """);

        var actualTasks = programmingExerciseTaskService.getTasksWithoutInactiveTestCases(programmingExercise.getId());
        assertThat(actualTasks).hasSize(1);

        var actualTestCases = actualTasks.stream().findFirst().get().getTestCases();
        assertThat(actualTestCases).allMatch(ProgrammingExerciseTestCase::isActive);
        assertThat(actualTestCases).hasSize(2);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testParseTestCaseNames() {
        programmingExercise = programmingExerciseRepository
                .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxRepos(programmingExercise.getId()).orElseThrow();
        programmingExerciseTestCaseRepository.deleteAll(programmingExercise.getTestCases());

        String[] testCaseNames = new String[] { "testClass[BubbleSort]", "testWithBraces()", "testParametrized(Parameter1, 2)[1]" };
        for (var name : testCaseNames) {
            var testCase = new ProgrammingExerciseTestCase();
            testCase.setExercise(programmingExercise);
            testCase.setTestName(name);
            testCase.setActive(true);
            programmingExerciseTestCaseRepository.save(testCase);
        }
        programmingExercise = programmingExerciseRepository
                .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxRepos(programmingExercise.getId()).orElseThrow();

        updateProblemStatement("""
                [task][Task 1](testClass[BubbleSort],testWithBraces(),testParametrized(Parameter1, 2)[1])
                """);

        var actualTasks = programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId());
        assertThat(actualTasks).hasSize(1);
        final var actualTask = actualTasks.stream().findAny().orElseThrow().getId();
        var actualTaskWithTestCases = programmingExerciseTaskRepository.findByIdWithTestCaseAndSolutionEntriesElseThrow(actualTask);
        assertThat(actualTaskWithTestCases.getTaskName()).isEqualTo("Task 1");
        var actualTestCaseNames = actualTaskWithTestCases.getTestCases().stream().map(ProgrammingExerciseTestCase::getTestName).toList();
        assertThat(actualTestCaseNames).containsExactlyInAnyOrder(testCaseNames);
    }

    private boolean checkTaskEqual(ProgrammingExerciseTask task, String expectedName, String expectedTestName) {
        var testCases = task.getTestCases();
        return expectedName.equals(task.getTaskName()) && !testCases.isEmpty() && expectedTestName.equals(testCases.stream().findFirst().get().getTestName());
    }
}
