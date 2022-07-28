package de.tum.in.www1.artemis.service.hestia;

import java.util.*;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
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
     *
     * This is coupled to the value used in `ProgrammingExerciseTaskExtensionWrapper` and `TaskCommand` in the client
     * If you change the regex, make sure to change it in all places!
     */
    private final Pattern taskPatternForProblemStatementMarkdown = Pattern.compile("\\[task]\\[(?<name>[^\\[\\]]+)]\\((?<tests>.*)\\)");

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
        return sortedExtractedTasks.stream().map(extractedTask -> unsortedTasks.stream().filter(task -> task.equals(extractedTask)).findFirst().orElse(null))
                .filter(Objects::nonNull).toList();
    }

    /**
     * Returns the extracted tasks and test cases from the problem statement markdown and
     * maps the tasks to the corresponding test cases for a programming exercise
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
        var matcher = taskPatternForProblemStatementMarkdown.matcher(problemStatement);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);
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
     * Get the test case names from the captured group by splitting by ',' if there are no unclosed rounded brackets for
     * the current test case. This respects the fact that parameterized tests may contain commas.
     * Example: "testInsert(InsertMock, 1),testClass[SortStrategy],testWithBraces()" results in the following list
     * ["testInsert(InsertMock, 1)", "testClass[SortStrategy]", "testWithBraces()"]
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
}
