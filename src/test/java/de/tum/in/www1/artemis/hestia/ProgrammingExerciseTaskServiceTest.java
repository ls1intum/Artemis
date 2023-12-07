package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.*;
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
        assertThat(actualTestCases).hasSize(2).allMatch(ProgrammingExerciseTestCase::isActive);
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
        final var actualTask = actualTasks.iterator().next().getId();
        var actualTaskWithTestCases = programmingExerciseTaskRepository.findByIdWithTestCaseAndSolutionEntriesElseThrow(actualTask);
        assertThat(actualTaskWithTestCases.getTaskName()).isEqualTo("Task 1");
        var actualTestCaseNames = actualTaskWithTestCases.getTestCases().stream().map(ProgrammingExerciseTestCase::getTestName).toList();
        assertThat(actualTestCaseNames).containsExactlyInAnyOrder(testCaseNames);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExtractTasksFromTestIds() {
        var test1 = programmingExerciseTestCaseRepository.findByExerciseIdAndTestName(programmingExercise.getId(), "testClass[BubbleSort]").orElseThrow();
        var test2 = programmingExerciseTestCaseRepository.findByExerciseIdAndTestName(programmingExercise.getId(), "testMethods[Context]").orElseThrow();

        updateProblemStatement("[task][Task 1](<testid>%s</testid>,<testid>%s</testid>)".formatted(test1.getId(), test2.getId()));

        var actualTasks = programmingExerciseTaskRepository.findByExerciseId(programmingExercise.getId());
        assertThat(actualTasks).hasSize(1);
        final var actualTask = actualTasks.iterator().next().getId();
        var actualTaskWithTestCases = programmingExerciseTaskRepository.findByIdWithTestCaseAndSolutionEntriesElseThrow(actualTask);
        assertThat(actualTaskWithTestCases.getTaskName()).isEqualTo("Task 1");
        var actualTestCaseNames = actualTaskWithTestCases.getTestCases().stream().map(ProgrammingExerciseTestCase::getTestName).toList();
        assertThat(actualTestCaseNames).containsExactlyInAnyOrder("testClass[BubbleSort]", "testMethods[Context]");
    }

    private boolean checkTaskEqual(ProgrammingExerciseTask task, String expectedName, String expectedTestName) {
        var testCases = task.getTestCases();
        return expectedName.equals(task.getTaskName()) && !testCases.isEmpty() && expectedTestName.equals(testCases.stream().findFirst().get().getTestName());
    }

    @Test
    void testNameReplacement() {
        Map<String, Long> testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, ProgrammingExerciseTestCase::getId));

        programmingExerciseTaskService.replaceTestNamesWithIds(programmingExercise);
        String problemStatement = programmingExercise.getProblemStatement();

        // [task][Task 1](testClass[BubbleSort])
        // [task][Task 2](testMethods[Context])
        assertThat(problemStatement).as("no more test case names should be present").doesNotContain(testCases.keySet())
                .contains("[task][Task 1](<testid>%s</testid>)".formatted(testCases.get("testClass[BubbleSort]")))
                .contains("[task][Task 2](<testid>%s</testid>)".formatted(testCases.get("testMethods[Context]")));
    }

    @Test
    void testNameReplacementKeepsInactiveTests() {
        // Task 1 is inactive, task 2 does not exist
        updateProblemStatement("[task][Task 1](testClass[BubbleSort])\n[task][Task 2](nonExistingTask)");
        var testCase = programmingExerciseTestCaseRepository.findByExerciseIdAndTestName(programmingExercise.getId(), "testClass[BubbleSort]").orElseThrow();
        testCase.setActive(false);
        programmingExerciseTestCaseRepository.save(testCase);

        programmingExerciseTaskService.replaceTestNamesWithIds(programmingExercise);
        String problemStatement = programmingExercise.getProblemStatement();

        // Task 1 still contains the name since the mentioned test case is not active
        assertThat(problemStatement).contains("[task][Task 1](testClass[BubbleSort])").contains("[task][Task 2](nonExistingTask)");
    }

    @Test
    void testNameReplacementSpecialNames() {
        var bubbleSort = programmingExerciseTestCaseRepository.findByExerciseIdAndTestName(programmingExercise.getId(), "testClass[BubbleSort]").orElseThrow();
        var braces = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testWithBraces()");
        var parameterized = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testParametrized(Parameter1, 2)[1]");
        updateProblemStatement("""
                [task][Task 1](testClass[BubbleSort],testWithBraces(),testParametrized(Parameter1, 2)[1])
                """);

        programmingExerciseTaskService.replaceTestNamesWithIds(programmingExercise);
        String problemStatement = programmingExercise.getProblemStatement();

        assertThat(problemStatement).as("no more test case names should be present")
                .doesNotContain("testClass[BubbleSort]", "testWithBraces()", "testParametrized(Parameter1, 2)[1]")
                .contains("[task][Task 1](<testid>%s</testid>,<testid>%s</testid>,<testid>%s</testid>)".formatted(bubbleSort.getId(), braces.getId(), parameterized.getId()));
    }

    @Test
    void replacePlantUMLTestsWithIds() {
        var testConstructorsLinkedList = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testConstructors[LinkedList]");
        var testMethodsLinkedList = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testMethods[LinkedList]");
        var testAttributesListNode = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testAttributes[ListNode]");
        var testConstructorsListNode = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testConstructors[ListNode]");
        var testClassLinkedList = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testClass[LinkedList]");
        var testAttributesLinkedList = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testAttributes[LinkedList]");
        updateProblemStatement("""
                @startuml

                class LinkedList<T> {
                    <color:testsColor(testConstructors[LinkedList])>+ LinkedList()</color>
                    <color:testsColor(testMethods[LinkedList])>:+ toString(): String </color>
                }

                class ListNode<T> {
                    <color:testsColor(testAttributes[ListNode])>- value: T</color>
                    <color:testsColor(testConstructors[ListNode])>+ ListNode()</color>
                    <color:testsColor(testConstructors[ListNode])>+ ListNode(T)</color>
                    <color:testsColor(testConstructors[ListNode])>+ ListNode(T, ListNode<T>, ListNode<T>)</color>
                }

                LinkedList -[hidden]> ListNode

                LinkedList --up-|> MyList #testsColor(testClass[LinkedList])
                LinkedList --> ListNode #testsColor(testAttributes[LinkedList]): first
                LinkedList --> ListNode #testsColor(testAttributes[LinkedList]): last
                ListNode -l-> ListNode #testsColor(testAttributes[ListNode]): previous
                ListNode -r-> ListNode #testsColor(testAttributes[ListNode]): next

                hide empty fields
                hide empty methods
                hide circles

                @enduml""");

        programmingExerciseTaskService.replaceTestNamesWithIds(programmingExercise);
        String problemStatement = programmingExercise.getProblemStatement();

        var allTests = List.of(testConstructorsLinkedList, testMethodsLinkedList, testAttributesListNode, testConstructorsListNode, testClassLinkedList, testAttributesLinkedList);
        var allTestNames = allTests.stream().map(ProgrammingExerciseTestCase::getTestName).toList();
        var allExpectedReplacements = allTests.stream().map(DomainObject::getId).map(id -> "<testid>" + id + "</testid>").toList();

        assertThat(problemStatement).as("All test names got replaced").doesNotContain(allTestNames).contains(allExpectedReplacements);
    }

    @Test
    void testNameReplacementOnlyWithinTasks() {
        // Tests that the replacing of test names only addresses test names inside of tasks.
        // If a test name gets used in the regular problem statement text it gets kept.

        var test1 = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "test");
        var test2 = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "taskTest");

        updateProblemStatement("[task][Taskname](test, taskTest)\nThis description contains the words test and taskTest, which should not be replaced.");

        programmingExerciseTaskService.replaceTestNamesWithIds(programmingExercise);
        String problemStatement = programmingExercise.getProblemStatement();

        assertThat(problemStatement).contains("[task][Taskname](<testid>%s</testid>,<testid>%s</testid>)".formatted(test1.getId(), test2.getId()))
                .contains("This description contains the words test and taskTest, which should not be replaced.");
    }

    @Test
    void testNameReplacementTaskNameSameAsTestName() {
        var sort = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "sort");

        updateProblemStatement("""
                [task][sort](sort)
                Sort using the method sort.
                @startuml
                class LinkedList<T> {
                    <color:testsColor(sort)>+ sort()</color>
                }
                @enduml""");

        programmingExerciseTaskService.replaceTestNamesWithIds(programmingExercise);
        String problemStatement = programmingExercise.getProblemStatement();

        assertThat(problemStatement).contains("[task][sort](<testid>%s</testid>)".formatted(sort.getId())).contains("Sort using the method sort.")
                .contains("<color:testsColor(<testid>%s</testid>)>+ sort()</color>".formatted(sort.getId()));
    }

    @Test
    void testIdReplacementWithNames() {
        var bubbleSort = programmingExerciseTestCaseRepository.findByExerciseIdAndTestName(programmingExercise.getId(), "testClass[BubbleSort]").orElseThrow();
        var inactiveTest = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testName");
        inactiveTest.setActive(false);
        programmingExerciseTestCaseRepository.save(inactiveTest);

        updateProblemStatement("[task][Taskname](<testid>%s</testid>,<testid>%s</testid>)".formatted(bubbleSort.getId(), inactiveTest.getId()));

        programmingExerciseTaskService.replaceTestIdsWithNames(programmingExercise);
        String problemStatement = programmingExercise.getProblemStatement();

        // inactive tests should also get replaced
        assertThat(problemStatement).doesNotContain("<testid>").contains("testClass[BubbleSort]", "testName");
    }

    @Test
    void testUpdateIds() {
        updateProblemStatement("[task][Taskname](<testid>1</testid>,<testid>2</testid>)");

        programmingExerciseTaskService.updateTestIds(programmingExercise, Map.of(1L, 10L, 2L, 23L));
        String problemStatement = programmingExercise.getProblemStatement();

        assertThat(problemStatement).contains("<testid>10</testid>", "<testid>23</testid>");
    }
}
