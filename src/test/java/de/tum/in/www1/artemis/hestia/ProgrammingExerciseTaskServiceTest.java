package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

class ProgrammingExerciseTaskServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        database.addCourseWithOneProgrammingExerciseAndSpecificTestCases();
        database.addUsers(2, 2, 1, 2);

        programmingExercise = programmingExerciseRepository.findAll().get(0);
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

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    void testNewExercise() {
        assertThat(programmingExerciseTaskRepository.findAll()).hasSize(2);
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
        assertThat(programmingExerciseTaskRepository.findAll()).hasSize(3);
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
        assertThat(programmingExerciseTaskRepository.findAll()).isEmpty();
    }

    private String ps = """
            # Obverver Pattern

            The city of Munich wants to contribute to a sustainable environment by offering innovative, safe, and user-friendly means of transport: the PEVolve system. Riders use a mobile app on their smartphone to rent three types of personal electric vehicles (PEVs): E-Moped, E-Bikes (electronic bicycle/pedelec), and E-Kickscooter.
            Each PEV has internet access and reports its location regularly so that riders can locate nearby PEVs with the mobile app.
            Riders have to set up an online account for payment within the app before they can use it to rent a PEV or reserve it for later use.
            Riders may bring their own helmet to ride an E-Bike. For E-Kickscooters, the age limit is 16 and riders must bring their own helmets to be allowed to ride.
            An E-Moped rider needs to have a valid driver's license and may take one additional rider on the back seat. The helmets are included.
            You are a software engineer and part of the development team at the e-beiksters GmbH, which was contracted by the city of Munich to set up and maintain its fleet of PEVs.

            ### Your Task

            Riders want to see in real time, which PEVs are available for rent. Use the `Notification + push` variant of the `Observer Pattern` to observe available and rented PEVs:

            1. [task][Implement Subject and Observer using Java generics.](test_structure_abstractClass(), test_structure_subscribe(), test_structure_unsubscribe())
              An `Observer<T>` can `subscribe` and `unsubscribe` by calling the correspoding methods on `Subject<T>`.
            2. [task][The Observer<T> can be updated by the update(T) method](test_structure_update(), test_structure_interface())
            3. [task][The Subject<T> can notifyObservers() with the changes returned by its abstract method getUpdate().](test_structure_notifyObservers(), test_structure_getUpdate(), test_functionality_observer_1(), test_functionality_observer_2())
            4. [task][The changes passed to the riders need to be represented by a new class.](test_structure_updateClass(), test_structure_isSubject(), test_structure_overrideGetUpdate(), test_structure_overrideUpdate(), test_structure_isObserver(), test_structure_genericSuperinterface(), test_structure_genericSuperclass())
              Create a class that references a `PEV` whose availability changed and has a `boolean` attribute that tells if the PEV has now become available or not. \s
              Modify `Rider` and `PEV` to realize the notification and push observer pattern with the new class.
            5. [task][Add the observer pattern to the PEV system](test_behavior_basic(), test_behavior_moreSteps(), test_behavior_unsubscribe())\s
              When the `lock()` or `unlock()` method is called on a `PEV`, a subscribed `Rider` should be notified using the already provided method `notifyAvailabilityChanged()` in `Rider`.


            #### Notes
            - The template provides an `Application` class that contains a `main()` method that allows you to test the implementation locally.\s
            - Pay close attention to what functionality is already provided in the template to avoid redundant implementations!

            """;

    @Test
    void testTest() {
        updateProblemStatement(ps);
        var tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId());
        assertThat(tasks).hasSize(5);
        updateProblemStatement(ps);
        tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId());
        assertThat(tasks).hasSize(5);
    }

    @Test
    void testReduceToOneTask() {
        updateProblemStatement("[task][Task 1](testClass[BubbleSort],testMethods[Context], testMethods[Policy])");
        assertThat(programmingExerciseTaskRepository.findAll()).hasSize(1);
        var tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(programmingExercise.getId());
        assertThat(tasks).hasSize(1);
        var task = tasks.stream().findFirst().get();
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

        assertThat(programmingExerciseTaskRepository.findAll()).hasSize(2);
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

        assertThat(programmingExerciseTaskRepository.findAll()).hasSize(2);

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
        assertThat(programmingExerciseTaskRepository.findAll()).hasSize(1);
        assertThat(programmingExerciseTaskRepository.findById(task.getId())).isEmpty();
        assertThat(codeHintRepository.findAll()).isEmpty();
    }

    @Test
    void testParseTestCaseNames() {
        String[] testCaseNames = new String[] { "testClass[BubbleSort]", "testWithBraces()", "testParametrized(Parameter1, 2)[1]" };
        for (var name : testCaseNames) {
            var testCase = new ProgrammingExerciseTestCase();
            testCase.setExercise(programmingExercise);
            testCase.setTestName(name);
            testCase.setActive(true);
            programmingExerciseTestCaseRepository.save(testCase);
        }
        updateProblemStatement("""
                [task][Task 1](testClass[BubbleSort],testWithBraces(),testParametrized(Parameter1, 2)[1])
                """);
        var actualTasks = programmingExerciseTaskRepository.findAll();
        assertThat(actualTasks).hasSize(1);
        var actualTaskWithTestCases = programmingExerciseTaskRepository.findByIdWithTestCaseAndSolutionEntriesElseThrow(actualTasks.get(0).getId());
        assertThat(actualTaskWithTestCases.getTaskName()).isEqualTo("Task 1");
        var actualTestCaseNames = actualTaskWithTestCases.getTestCases().stream().map(ProgrammingExerciseTestCase::getTestName).toList();
        assertThat(actualTestCaseNames).containsExactlyInAnyOrder(testCaseNames);
    }

    private boolean checkTaskEqual(ProgrammingExerciseTask task, String expectedName, String expectedTestName) {
        var testCases = task.getTestCases();
        return expectedName.equals(task.getTaskName()) && !testCases.isEmpty() && expectedTestName.equals(testCases.stream().findFirst().get().getTestName());
    }
}
