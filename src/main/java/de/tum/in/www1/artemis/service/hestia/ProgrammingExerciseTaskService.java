package de.tum.in.www1.artemis.service.hestia;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;

@Service
public class ProgrammingExerciseTaskService {

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ExerciseHintRepository exerciseHintRepository;

    /**
     * Pattern that is used to extract the tasks (capturing group "name") and test case names (capturing group "tests") from the problem statement.
     * Example: "[task][Implement BubbleSort](testBubbleSort,testBubbleSortHidden)". Following values are extracted by the named capturing groups:
     * - name: "Implement BubbleSort"
     * - tests: "testBubbleSort,testBubbleSortHidden"
     * <p>
     * This is coupled to the value used in `ProgrammingExerciseTaskExtensionWrapper` and `TaskCommand` in the client
     * If you change the regex, make sure to change it in all places!
     */
    private static final Pattern TASK_PATTERN = Pattern.compile("\\[task]\\[(?<name>[^\\[\\]]+)]\\((?<tests>.*)\\)");

    private static final Pattern PLANTUML_PATTERN = Pattern.compile("@startuml([^@]*)@enduml");

    private static final Pattern TESTSCOLOR_PATTERN = Pattern.compile("testsColor\\(((?:[^()]+\\([^()]*\\))*[^()]*)\\)");

    private static final String TESTID_START = "<testid>";

    private static final String TESTID_END = "</testid>";

    private static final Pattern TESTID_PATTERN = Pattern.compile(TESTID_START + "(\\d+)" + TESTID_END);

    public ProgrammingExerciseTaskService(ProgrammingExerciseTaskRepository programmingExerciseTaskRepository,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, ExerciseHintRepository exerciseHintRepository) {
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.exerciseHintRepository = exerciseHintRepository;
    }

    /**
     * Deletes a ProgrammingExerciseTask together with its CodeHints
     * This has to be manually done, as there is no orphanRemoval between the two entities
     *
     * @param task The task to delete
     */
    public void delete(ProgrammingExerciseTask task) {
        var exerciseHints = exerciseHintRepository.findByTaskId(task.getId());
        exerciseHintRepository.deleteAll(exerciseHints);
        programmingExerciseTaskRepository.delete(task);
    }

    /**
     * Extracts all tasks from the problem statement of an exercise and saves them to the database.
     * All tasks that no longer exist will be deleted.
     * If there is already a task with the same test cases as a new one, but with a different name the existing one will be renamed.
     *
     * @param exercise The programming exercise to extract the tasks from
     * @return The current tasks of the exercise
     */
    public Set<ProgrammingExerciseTask> updateTasksFromProblemStatement(ProgrammingExercise exercise) {
        var previousTasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(exercise.getId());
        var extractedTasks = new HashSet<>(extractTasks(exercise));
        // No changes
        if (previousTasks.equals(extractedTasks)) {
            return previousTasks;
        }
        // Add all tasks that did not change
        var tasksToBeSaved = new HashSet<>(previousTasks);
        tasksToBeSaved.retainAll(extractedTasks);
        var tasksToBeDeleted = new HashSet<>(previousTasks);
        tasksToBeDeleted.removeAll(tasksToBeSaved);
        var tasksToBeAdded = new HashSet<>(extractedTasks);
        tasksToBeAdded.removeAll(tasksToBeSaved);
        // Check for tasks where only the name changed
        Iterator<ProgrammingExerciseTask> extractedTaskIterator = tasksToBeAdded.iterator();
        while (extractedTaskIterator.hasNext()) {
            ProgrammingExerciseTask extractedTask = extractedTaskIterator.next();
            Iterator<ProgrammingExerciseTask> previousTaskIterator = tasksToBeDeleted.iterator();
            while (previousTaskIterator.hasNext()) {
                ProgrammingExerciseTask previousTask = previousTaskIterator.next();
                if (previousTask.getTestCases().equals(extractedTask.getTestCases())) {
                    previousTask.setTaskName(extractedTask.getTaskName());
                    tasksToBeSaved.add(previousTask);
                    extractedTaskIterator.remove();
                    previousTaskIterator.remove();
                    break;
                }
            }
        }
        // Add all newly created tasks
        tasksToBeSaved.addAll(tasksToBeAdded);
        // Remove old tasks
        for (ProgrammingExerciseTask task : tasksToBeDeleted) {
            delete(task);
        }
        // Save all tasks
        for (ProgrammingExerciseTask task : tasksToBeSaved) {
            task.setExercise(exercise);
        }
        return new HashSet<>(programmingExerciseTaskRepository.saveAll(tasksToBeSaved));
    }

    /**
     * Gets the tasks of a programming exercise sorted by their order in the problem statement
     * TODO: Replace this with an @OrderColumn on tasks in ProgrammingExercise
     *
     * @param exercise The programming exercise
     * @return The sorted tasks
     */
    public List<ProgrammingExerciseTask> getSortedTasks(ProgrammingExercise exercise) {
        var unsortedTasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(exercise.getId());
        var sortedExtractedTasks = extractTasks(exercise);
        return sortedExtractedTasks.stream()
                .map(extractedTask -> unsortedTasks.stream()
                        .filter(task -> task.getTaskName().equals(extractedTask.getTaskName()) && task.getTestCases().equals(extractedTask.getTestCases())).findFirst()
                        .orElse(null))
                .filter(Objects::nonNull).toList();
    }

    /**
     * Gets all tasks of an exercise excluding inactive test cases
     *
     * @param exerciseId of the programming exercise
     * @return Set of all tasks and its test cases
     */
    public Set<ProgrammingExerciseTask> getTasksWithoutInactiveTestCases(long exerciseId) {
        return programmingExerciseTaskRepository.findByExerciseIdWithTestCaseAndSolutionEntriesElseThrow(exerciseId).stream()
                .peek(task -> task.getTestCases().removeIf(Predicate.not(ProgrammingExerciseTestCase::isActive))).collect(Collectors.toSet());
    }

    /**
     * Gets all tasks of an exercise including the test cases assigned to those tasks.
     * Additionally, adds a new task for all test cases with no manually assigned task and adds all tests to that task
     *
     * @param exerciseId of the programming exercise
     * @return Set of all tasks including one for not manually assigned tests
     */
    public Set<ProgrammingExerciseTask> getTasksWithUnassignedTestCases(long exerciseId) {
        Set<ProgrammingExerciseTask> tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCaseAndSolutionEntriesElseThrow(exerciseId);

        Set<ProgrammingExerciseTestCase> testsWithTasks = tasks.stream().flatMap(task -> task.getTestCases().stream()).collect(Collectors.toSet());

        // Additionally add all tests that are not manually assigned to a task
        Set<ProgrammingExerciseTestCase> testsWithoutTasks = programmingExerciseTestCaseRepository.findByExerciseId(exerciseId).stream()
                .filter(test -> !testsWithTasks.contains(test)).collect(Collectors.toSet());

        if (!testsWithoutTasks.isEmpty()) {
            ProgrammingExerciseTask unassignedTask = new ProgrammingExerciseTask();
            unassignedTask.setTaskName("Not assigned to task");
            unassignedTask.setTestCases(testsWithoutTasks);

            tasks.add(unassignedTask);
        }

        return tasks;
    }

    /**
     * Returns the extracted tasks and test cases from the problem statement markdown and
     * maps the tasks to the corresponding test cases for a programming exercise.
     *
     * @param exercise the exercise for which the tasks and test cases should be extracted
     * @return the extracted tasks with the corresponding test cases
     */
    private List<ProgrammingExerciseTask> extractTasks(ProgrammingExercise exercise) {
        var tasks = new ArrayList<ProgrammingExerciseTask>();
        var problemStatement = exercise.getProblemStatement();
        if (problemStatement == null || problemStatement.isEmpty()) {
            return tasks;
        }
        var matcher = TASK_PATTERN.matcher(problemStatement);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(exercise.getId());
        while (matcher.find()) {
            var taskName = matcher.group("name");
            var capturedTestCaseNames = matcher.group("tests");

            var task = new ProgrammingExerciseTask();
            task.setTaskName(taskName);
            task.setExercise(exercise);
            var testCaseNames = extractTestCaseNames(capturedTestCaseNames);

            for (String testName : testCaseNames) {
                testCases.stream().filter(tc -> tc.getTestName().equals(testName)).findFirst().ifPresent(task.getTestCases()::add);
            }
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * Replaces a comma seperated list of test case names with their corresponding ids.
     * If no matching test case exists (e.g. due to a typo) the test name get kept.
     */
    private String extractTestCaseIdsFromNames(String capturedTestCaseNames, Set<ProgrammingExerciseTestCase> testCases) {
        var testCaseNames = extractTestCaseNames(capturedTestCaseNames);

        return testCaseNames.stream().map(testName -> convertTestNameToTestId(testName, testCases)).collect(Collectors.joining(","));
    }

    private String convertTestNameToTestId(String testName, Set<ProgrammingExerciseTestCase> testCases) {
        return testCases.stream().filter(tc -> testName.equals(tc.getTestName())).findFirst().map(tc -> tc.getId().toString()).map(id -> TESTID_START + id + TESTID_END)
                .orElse(testName);
    }

    private String extractTestNamesFromIds(String capturedTestCaseIds, Set<ProgrammingExerciseTestCase> testCases) {
        var capturedTestIds = extractTestCaseNames(capturedTestCaseIds);

        return capturedTestIds.stream().map(tc -> convertTestIdToTestName(tc, testCases)).collect(Collectors.joining(","));
    }

    private String convertTestIdToTestName(String testId, Set<ProgrammingExerciseTestCase> testCases) {
        Long id = extractTestId(testId);

        if (id == null) {
            // no matching test case e.d. due to a typo, leave it as it is
            return testId;
        }

        return testCases.stream().filter(tc -> tc.getId().equals(id)).findFirst().map(ProgrammingExerciseTestCase::getTestName).orElse(testId);
    }

    private Long extractTestId(String test) {
        var matcher = TESTID_PATTERN.matcher(test);
        if (!matcher.find()) {
            // This not a test id but a name that got not replaced previously (e.g. due to a typo)
            return null;
        }

        try {
            return Long.parseLong(matcher.group(1));
        }
        catch (NumberFormatException ignore) {
            return null;
        }
    }

    /**
     * Get the test case names from the captured group by splitting by ',' if there are no unclosed rounded brackets for
     * the current test case. This respects the fact that parameterized tests may contain commas.
     * Example: "testInsert(InsertMock, 1),testClass[SortStrategy],testWithBraces()" results in the following list
     * ["testInsert(InsertMock, 1)", "testClass[SortStrategy]", "testWithBraces()"]
     *
     * @param capturedTestCaseNames the captured test case names matched from the problem statement
     * @return test case names
     */
    private List<String> extractTestCaseNames(String capturedTestCaseNames) {
        List<String> testCaseNames = new ArrayList<>();
        if ("".equals(capturedTestCaseNames)) {
            return testCaseNames;
        }

        int numberUnclosedRoundedBrackets = 0;
        StringBuilder currentTestCaseName = new StringBuilder();
        for (int i = 0; i < capturedTestCaseNames.length(); i++) {
            char currentChar = capturedTestCaseNames.charAt(i);

            // check potential split
            if (currentChar == ',' && numberUnclosedRoundedBrackets == 0) {
                testCaseNames.add(currentTestCaseName.toString().trim());
                currentTestCaseName = new StringBuilder();
                continue;
            }

            // count the numbers of brackets
            if (currentChar == '(') {
                numberUnclosedRoundedBrackets++;
            }
            else if (currentChar == ')') {
                numberUnclosedRoundedBrackets--;
            }

            currentTestCaseName.append(currentChar);
        }

        testCaseNames.add(currentTestCaseName.toString().trim());

        return testCaseNames;
    }

    /**
     * Replaces the test names embedded into the problem statement with their corresponding id.
     * The result does not get saved yet.
     * <p>
     * Example:
     * Input: [task][Implement BubbleSort](testBubbleSort, testClass[BubbleSort])
     * Output: [task][Implement BubbleSort](<testid>15</testid>,<testid>18</testid>)
     *
     * @param exercise the exercise to replaces the test names in the problem statement
     */
    public void replaceTestNamesWithIds(ProgrammingExercise exercise) {
        replaceInProblemStatement(exercise, this::extractTestCaseIdsFromNames);
    }

    /**
     * Prepares a saved problem statement (with test ids) for editors.
     * Replaces the test ids with test names.
     */
    public void replaceTestIdsWithNames(ProgrammingExercise exercise) {
        replaceInProblemStatement(exercise, this::extractTestNamesFromIds);
    }

    private void replaceInProblemStatement(ProgrammingExercise exercise, BiFunction<String, Set<ProgrammingExerciseTestCase>, String> replacer) {
        var problemStatement = exercise.getProblemStatement();
        if (problemStatement == null || problemStatement.isEmpty()) {
            return;
        }
        // only replace active test cases (test cases that also exist in the test repository)
        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);

        if (testCases.isEmpty()) {
            return;
        }

        problemStatement = replaceTaskTests(problemStatement, testCases, replacer);
        problemStatement = replacePlantUMLTestCases(problemStatement, testCases, replacer);

        exercise.setProblemStatement(problemStatement);
    }

    private String replaceTaskTests(String problemStatement, Set<ProgrammingExerciseTestCase> testCases, BiFunction<String, Set<ProgrammingExerciseTestCase>, String> replacer) {
        Matcher matcher = TASK_PATTERN.matcher(problemStatement);

        return matcher.replaceAll(matchResult -> {
            // matchResult is fa full task, e.g. [task][Bubble Sort](testBubbleSort,testClass[BubbleSort])
            String fullMatch = matchResult.group();
            // group 1: task name, group 2: test names. e.g testBubbleSort,testClass[BubbleSort]
            String testNames = matchResult.group(2);

            // converted testids, e.g. <testid>10</testid>,<testid>12</testid>
            String testIds = replacer.apply(testNames, testCases);

            // replace the names with their ids
            return fullMatch.replace(testNames, testIds);
        });
    }

    private String replacePlantUMLTestCases(String problemStatement, Set<ProgrammingExerciseTestCase> testCases,
            BiFunction<String, Set<ProgrammingExerciseTestCase>, String> replacer) {
        Matcher matcher = PLANTUML_PATTERN.matcher(problemStatement);

        return matcher.replaceAll(matchResult -> {
            // matchResult: Full UML diagramg (everything between @startuml and @enduml)
            String diagram = matchResult.group();
            Matcher tests = TESTSCOLOR_PATTERN.matcher(diagram);
            return tests.replaceAll(testsMatchResult -> {
                // testsMatchResult: one testscolor instance, e.g. testsColor(testAttributes[BubbleSort])
                String fullMatch = testsMatchResult.group();
                // group 1: test names, e.g testAttributes[BubbleSort]
                String testNames = testsMatchResult.group(1);
                // ids to insert, e.g. <testid>15</testid>
                String testIds = replacer.apply(testNames, testCases);
                return fullMatch.replace(testNames, testIds);
            });
        });
    }

    // TODO: Double check the hint integration (both directions)

    /**
     * Updates the existing testids to the newly provided ids. Used when importing programming exercises.
     *
     * @param exercise             the exercise to replace the ids in.
     * @param newTestCaseIdByOldId a map indicating which ids should be replaced with their corresponding new ones.
     */
    public void updateTestIds(ProgrammingExercise exercise, Map<Long, Long> newTestCaseIdByOldId) {
        replaceInProblemStatement(exercise, ((capture, testCases) -> {
            // Input old ids (<testid>27</testid>), output new ids (<testid>123</testid>)
            var capturedTestIds = extractTestCaseNames(capture);

            return capturedTestIds.stream().map(tc -> {
                Long id = extractTestId(tc);
                if (id == null) {
                    return tc;
                }
                if (newTestCaseIdByOldId.containsKey(id)) {
                    return TESTID_START + newTestCaseIdByOldId.get(id) + TESTID_END;
                }
                return tc;
            }).collect(Collectors.joining(","));
        }));
    }
}
