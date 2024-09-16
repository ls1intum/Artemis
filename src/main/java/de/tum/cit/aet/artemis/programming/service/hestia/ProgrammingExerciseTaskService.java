package de.tum.cit.aet.artemis.programming.service.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ExerciseHintRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseTaskRepository;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseTaskService {

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ExerciseHintRepository exerciseHintRepository;

    /**
     * Pattern that is used to extract the tasks (capturing group {@code name}) and test case names (capturing groups {@code tests}) from the problem statement.
     * Example: "[task][Implement BubbleSort](testBubbleSort,testBubbleSortHidden)". Following groups are extracted by the capturing groups:
     * <ul>
     * <li>name: {@code Implement BubbleSort}
     * <li>tests: {@code testBubbleSort,testBubbleSortHidden}
     * </ul>
     * <p>
     * The first section - withing square brackets - captures the task identifier {@code [task]}.<br>
     * The second section {@code name} - within square brackets - matches the task name, allowing any characters but square brackets.<br>
     * The third and last section {@code tests} - within round brackets - captures zero, one or multiple test cases. Multiple test cases get separated by a comma and optional
     * whitespace. Each test case contains a test name with or without round brackets. These round brackets may contain method parameters. Test names may contain any characters but
     * round brackets, whitespace or commas. Method parameters only exclude round brackets. After round brackets a test case may contain additional characters excluding round
     * brackets or commas.<br>
     * Therefore, allowed test names are among others {@code testName}, {@code testName()}, {@code testName(1234, 12)}, {@code testName(testValue)[1]}, {@code Test Name}.<br>
     * For multiple testcases it's {@code testName,otherTestName()} or {@code testName,     otherTestName()}.<br>
     * <p>
     * This is coupled to the value used in `ProgrammingExerciseTaskExtensionWrapper`, `ProgrammingExerciseInstructionAnalysisService`, and `TaskCommand` in the client
     * If you change the regex, make sure to change it in all places!
     */
    private static final Pattern TASK_PATTERN = Pattern
            .compile("\\[task]\\[(?<name>[^\\[\\]]+)]\\((?<tests>(?:[^(),]+(?:\\([^()]*\\)[^(),]*)?(?:,[^(),]+(?:\\([^()]*\\)[^(),]*)?)*)?)\\)");

    /**
     * Regex to find PlantUML diagrams inside a problem statement.
     * This matches everything starting with {@code @startuml} and ending with {@code @enduml}.
     * The capture group 1 will be the content of the diagram (everything besides {@code @startuml} and {@code @enduml})
     */
    private static final Pattern PLANTUML_PATTERN = Pattern.compile("@startuml([^@]*)@enduml");

    /**
     * Regex to find test cases inside a PlantUML diagram.
     * Instructors can change the color of UML elements by using e.g {@code <color:testsColor(testConstructors[LinkedList])>+ LinkedList()</color>}
     * <p>
     * The first capture group of this pattern will contain the test name, in the example above {@code testConstructors[LinkedList]}.
     * It's currently not possible to assign multiple test cases to a single UML element.
     * <p>
     * This is coupled to the value used in `ProgrammingExercisePlantUmlExtensionWrapper` in the client.
     * If you change the regex, make sure to change it in all places!
     */
    private static final Pattern TESTSCOLOR_PATTERN = Pattern.compile("testsColor\\((\\s*+[^()\\s]++(\\([^()]*+\\))?)\\)");

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
                .distinct().filter(Objects::nonNull).toList();
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
    public List<ProgrammingExerciseTask> getTasksWithUnassignedTestCases(long exerciseId) {
        List<ProgrammingExerciseTask> tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCaseAndSolutionEntriesElseThrow(exerciseId);

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
            testCaseNames.stream().map(name -> findTestCaseFromProblemStatement(name, testCases)).flatMap(Optional::stream).forEach(task.getTestCases()::add);

            tasks.add(task);
        }
        return tasks;
    }

    private Optional<ProgrammingExerciseTestCase> findTestCaseFromProblemStatement(String testName, Set<ProgrammingExerciseTestCase> testCases) {
        if (testName.startsWith(TESTID_START)) {
            Long id = extractTestId(testName);
            return testCases.stream().filter(tc -> tc.getId().equals(id)).findFirst();
        }
        else {
            return testCases.stream().filter(tc -> tc.getTestName().equals(testName)).findFirst();
        }
    }

    /**
     * Finds the test case id wrapped into a <testid></testid> text.
     * Returns null if the text does not reference a testid, but is, e.g., a name instead.
     * <p>
     * Examples:
     * <ul>
     * <li>{@code <testid>17</testid> -> 17}
     * <li>{@code testBubbleSort -> null}
     * </ul>
     *
     * @param testidText The text found in the problem statement.
     * @return the id of the test case
     */
    private Long extractTestId(String testidText) {
        var matcher = TESTID_PATTERN.matcher(testidText);
        if (!matcher.find()) {
            // This is not a test id but a name that did not get replaced previously, e.g., due to a typo
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
                String currentName = currentTestCaseName.toString().strip();
                if (!currentName.isEmpty()) {
                    testCaseNames.add(currentName);
                }
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
     * Converts a test name to its id-reference replacement in the problem statement.
     * Example: {@code testBubbleSort() -> <testid>27</testid>}
     *
     * @param testName  the test name to replace
     * @param testCases all test cases of the exercise, used to find the correct id
     * @return the new replacement to be used in the problem statement
     */
    private String convertTestNameToTestIdReplacement(String testName, Set<ProgrammingExerciseTestCase> testCases) {
        for (ProgrammingExerciseTestCase testCase : testCases) {
            if (testName.equals(testCase.getTestName())) {
                String id = testCase.getId().toString();
                return TESTID_START + id + TESTID_END;
            }
        }
        return testName;
    }

    /**
     * Prepares a saved problem statement (with test ids) for editors.
     * Replaces the test ids with test names.
     * The problem statement of the passed exercise gets changed, but the result does not get saved.
     *
     * @param exercise The exercise where its problem statement is updated
     */
    public void replaceTestIdsWithNames(ProgrammingExercise exercise) {
        // Also replace inactive test cases; don't send any testids (e.g. ids referring to previously active test cases) to the editor.
        // The client will then show a warning that the mentioned test name no longer exists.
        replaceInProblemStatement(exercise, this::extractTestNamesFromTestIds, false);
    }

    private String extractTestNamesFromTestIds(String capturedTestCaseIds, Set<ProgrammingExerciseTestCase> testCases) {
        var capturedTestIds = extractTestCaseNames(capturedTestCaseIds);

        return capturedTestIds.stream().map(tc -> convertTestIdToTestName(tc, testCases)).collect(Collectors.joining(","));
    }

    private String convertTestIdToTestName(String testId, Set<ProgrammingExerciseTestCase> testCases) {
        Long id = extractTestId(testId);

        if (id == null) {
            // no matching test case e.d. due to a typo, leave it as it is
            return testId;
        }

        for (ProgrammingExerciseTestCase tc : testCases) {
            if (tc.getId().equals(id)) {
                String testName = tc.getTestName();
                return Objects.requireNonNullElse(testName, testId);
            }
        }
        return testId;
    }

    /**
     * Replaces the test names embedded into the problem statement with their corresponding id.
     * The problem statement of the passed exercise gets changed, but the result does not get saved.
     * <p>
     * Example:
     * Input: [task][Implement BubbleSort](testBubbleSort, testClass[BubbleSort])
     * Output: [task][Implement BubbleSort](<testid>15</testid>,<testid>18</testid>)
     *
     * @param exercise the exercise to replace the test names in the problem statement
     */
    public void replaceTestNamesWithIds(ProgrammingExercise exercise) {
        // Only replace active test cases (tests that actually exist in the repository).
        // The other test cases will get replaced as soon as they get active.
        replaceInProblemStatement(exercise, this::extractTestCaseIdReplacementsFromNames, true);
    }

    /**
     * Replaces a comma separated list of test case names with their corresponding id replacement.
     * We keep the test name if no matching test case exists (e.g. due to a typo).
     * <p>
     * Example: {@code testBubbleSort(),doesNotExists -> <testid>27</testid>,doesNotExists }
     */
    private String extractTestCaseIdReplacementsFromNames(String capturedTestCaseNames, Set<ProgrammingExerciseTestCase> testCases) {
        var testCaseNames = extractTestCaseNames(capturedTestCaseNames);

        return testCaseNames.stream().map(testName -> convertTestNameToTestIdReplacement(testName, testCases)).collect(Collectors.joining(","));
    }

    private void replaceInProblemStatement(ProgrammingExercise exercise, BiFunction<String, Set<ProgrammingExerciseTestCase>, String> replacer, boolean onlyActive) {
        var problemStatement = exercise.getProblemStatement();
        if (problemStatement == null || problemStatement.isEmpty()) {
            return;
        }
        Set<ProgrammingExerciseTestCase> testCases;
        if (onlyActive) {
            testCases = programmingExerciseTestCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);
        }
        else {
            testCases = programmingExerciseTestCaseRepository.findByExerciseId(exercise.getId());
        }

        if (testCases.isEmpty()) {
            return;
        }

        problemStatement = replaceTaskTests(problemStatement, testCases, replacer);
        problemStatement = replacePlantUMLTestCases(problemStatement, testCases, replacer);

        exercise.setProblemStatement(problemStatement);
    }

    /**
     * Looks for all tasks in the given problem statement, and replaces its mentioned test cases using the given replacer method.
     * Replacer methods are {@link ProgrammingExerciseTaskService#extractTestCaseIdReplacementsFromNames(String, Set)}
     * or {@link ProgrammingExerciseTaskService#extractTestNamesFromTestIds(String, Set)}.
     *
     * @param problemStatement the problem statement to replace the tasks
     * @param testCases        all test cases of the exercise; used to look up the new value to use
     * @param replacer         the replacer method that gets executed when a test case gets found
     * @return the new problem statement
     */
    private String replaceTaskTests(String problemStatement, Set<ProgrammingExerciseTestCase> testCases, BiFunction<String, Set<ProgrammingExerciseTestCase>, String> replacer) {
        Matcher matcher = TASK_PATTERN.matcher(problemStatement);

        return matcher.replaceAll(matchResult -> {
            // group 1: task name, group 2: test names, e.g, testBubbleSort,testClass[BubbleSort]
            String taskName = matchResult.group(1);
            String testNames = matchResult.group(2);

            // converted testids, e.g., <testid>10</testid>,<testid>12</testid>
            String testIds = replacer.apply(testNames, testCases);

            // construct a new task using the testids
            return "[task][%s](%s)".formatted(taskName, testIds);
        });
    }

    /**
     * Looks for all test cases integrated into plantuml diagrams in the given problem statement, and replaces its test case using the given replacer method.
     * Replacer methods are {@link ProgrammingExerciseTaskService#extractTestCaseIdReplacementsFromNames(String, Set)}
     * or {@link ProgrammingExerciseTaskService#extractTestNamesFromTestIds(String, Set)}.
     *
     * @param problemStatement the problem statement to replace the plantuml diagram tests
     * @param testCases        all test cases of the exercise; used to look up the new value to use
     * @param replacer         the replacer method that gets executed when a test case gets found
     * @return the new problem statement
     */
    private String replacePlantUMLTestCases(String problemStatement, Set<ProgrammingExerciseTestCase> testCases,
            BiFunction<String, Set<ProgrammingExerciseTestCase>, String> replacer) {
        Matcher matcher = PLANTUML_PATTERN.matcher(problemStatement);

        return matcher.replaceAll(matchResult -> {
            // matchResult: Full UML diagram (everything between @startuml and @enduml)
            String diagram = matchResult.group();
            Matcher tests = TESTSCOLOR_PATTERN.matcher(diagram);
            return tests.replaceAll(testsMatchResult -> {
                // testsMatchResult: one testscolor instance, e.g. testsColor(testAttributes[BubbleSort])
                String fullMatch = testsMatchResult.group();
                // group 1: test name, e.g, testAttributes[BubbleSort]
                String testName = testsMatchResult.group(1);
                // id to insert, e.g., <testid>15</testid>
                String testId = replacer.apply(testName, testCases);
                return fullMatch.replace(testName, testId);
            });
        });
    }

    /**
     * Updates the problem statement by replacing existing testid references with the newly provided ids.
     * Used when importing programming exercises.
     * <p>
     * Example: {@code <testid>27</testid> -> <testid>52</testid>}
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
        }), false);
    }
}
